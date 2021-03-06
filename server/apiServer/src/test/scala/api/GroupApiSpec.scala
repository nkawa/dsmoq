package api

import java.io.File
import java.util.UUID

import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._

import common.DsmoqSpec
import dsmoq.controllers.AjaxResponse
import dsmoq.persistence.GroupMemberRole
import dsmoq.services.User
import dsmoq.services.json.DatasetData.{ DatasetsSummary, Dataset }
import dsmoq.services.json.GroupData.Group
import dsmoq.services.json.GroupData.GroupAddImages
import dsmoq.services.json.GroupData.GroupDeleteImage
import dsmoq.services.json.GroupData.GroupsSummary
import dsmoq.services.json.GroupData.MemberSummary
import dsmoq.services.json.RangeSlice

class GroupApiSpec extends DsmoqSpec {
  private val dummyFile = new File("../README.md")
  private val dummyImage = new File("../../client/www/dummy/images/nagoya.jpg")
  private val dummyUserUUID = "eb7a596d-e50c-483f-bbc7-50019eea64d7" // dummy 4
  private val dummyUserLoginParams = Map("d" -> compact(render(("id" -> "dummy4") ~ ("password" -> "password"))))

  "API test" - {
    "dataset" - {
      "グループ一覧が取得できるか" in {
        session {
          signIn()
          val groupId = createGroup()

          val params = Map("d" -> compact(render(("limit" -> JInt(10)))))
          get("/api/groups", params) {
            status should be(200)
            val result = parse(body).extract[AjaxResponse[RangeSlice[GroupsSummary]]]
            result.data.summary.count should be(10)
            result.data.results.map(_.id).contains(groupId)
          }
        }
      }

      "グループが作成できるか" in {
        session {
          signIn()
          val groupId = createGroup()
          get("/api/groups/" + groupId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Group]]
            result.data.id should be(groupId)
          }
        }
      }

      "作成したグループが削除できるか" in {
        session {
          signIn()
          val groupId = createGroup()
          delete("/api/groups/" + groupId) { checkStatus() }
          get("/api/groups/" + groupId) {
            status should be(404)
            val result = parse(body).extract[AjaxResponse[Group]]
            result.status should be("NotFound")
          }
        }
      }

      "グループの情報が編集できるか" in {
        session {
          signIn()
          val groupId = createGroup()
          val changeGroupName = "changeName" + UUID.randomUUID().toString
          val changeDescription = "change description"
          val params = Map("d" -> compact(render(("name" -> changeGroupName) ~ ("description" -> changeDescription))))
          put("/api/groups/" + groupId, params) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Group]]
            result.data.name should be(changeGroupName)
            result.data.description should be(changeDescription)
          }
          get("/api/groups/" + groupId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Group]]
            result.data.name should be(changeGroupName)
            result.data.description should be(changeDescription)
          }
        }
      }

      "グループに画像が追加できるか" in {
        session {
          signIn()
          val groupId = createGroup()
          val images = Map("images" -> dummyImage)
          val imageId = post("/api/groups/" + groupId + "/images", Map.empty, images) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[GroupAddImages]]
            result.data.images(0).id
          }
          get("/api/groups/" + groupId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Group]]
            result.data.images.size should be(2)
            assert(result.data.images.map(_.id).contains(imageId))
          }
        }
      }

      "グループに追加した画像が削除できるか" in {
        session {
          signIn()
          val groupId = createGroup()

          // add images
          val images = Map("images" -> dummyImage)
          post("/api/groups/" + groupId + "/images", Map.empty, images) { checkStatus() }
          val imageId = post("/api/groups/" + groupId + "/images", Map.empty, images) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[GroupAddImages]]
            result.data.images(0).id
          }
          post("/api/groups/" + groupId + "/images", Map.empty, images) { checkStatus() }

          delete("/api/groups/" + groupId + "/images/" + imageId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[GroupDeleteImage]]
            result.data.primaryImage should not be (imageId)
          }
          get("/api/groups/" + groupId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Group]]
            result.data.images.size should be(3)
            assert(!result.data.images.map(_.id).contains(imageId))
          }
        }
      }

      "グループのメイン画像を変更できるか" in {
        session {
          signIn()
          val groupId = createGroup()

          // add images
          val images = Map("images" -> dummyImage)
          post("/api/groups/" + groupId + "/images", Map.empty, images) { checkStatus() }
          val imageId = post("/api/groups/" + groupId + "/images", Map.empty, images) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[GroupAddImages]]
            result.data.images(0).id
          }
          post("/api/groups/" + groupId + "/images", Map.empty, images) { checkStatus() }

          // change primary image
          val params = Map("d" -> compact(render(("imageId" -> imageId))))
          put("/api/groups/" + groupId + "/images/primary", params) { checkStatus() }

          get("/api/groups/" + groupId) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[Group]]
            result.data.primaryImage should be(imageId)
          }
        }
      }

      "グループメンバー一覧を取得できるか" in {
        session {
          signIn()
          val myUserId = get("/api/profile") {
            checkStatus()
            parse(body).extract[AjaxResponse[User]].data.id
          }

          val groupId = createGroup()
          val params = Map("d" -> compact(render(("limit" -> JInt(10)))))
          get("/api/groups/" + groupId + "/members", params) {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[MemberSummary]]]
            result.data.summary.count should be(10)
            assert(result.data.results.map(_.id).contains(myUserId))
          }
        }
      }

      "グループにメンバーを追加できるか" in {
        session {
          signIn()
          val groupId = createGroup()
          val params = Map("d" -> compact(render(Seq(("userId" -> dummyUserUUID) ~ ("role" -> JInt(GroupMemberRole.Member))))))
          post("/api/groups/" + groupId + "/members", params) { checkStatus() }

          get("/api/groups/" + groupId + "/members") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[MemberSummary]]]
            assert(result.data.results.map(_.id).contains(dummyUserUUID))
          }
        }
      }

      "グループメンバーのロールを変更できるか" in {
        session {
          signIn()
          val groupId = createGroup()
          val params = Map("d" -> compact(render(Seq(("userId" -> dummyUserUUID) ~ ("role" -> JInt(GroupMemberRole.Member))))))
          post("/api/groups/" + groupId + "/members", params) { checkStatus() }

          // ロール変更
          val changeParams = Map("d" -> compact(render(("role" -> JInt(GroupMemberRole.Manager)))))
          put("/api/groups/" + groupId + "/members/" + dummyUserUUID, changeParams) { checkStatus() }

          get("/api/groups/" + groupId + "/members") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[MemberSummary]]]
            // IDの有無をチェック後、付随するデータのチェック
            assert(result.data.results.map(_.id).contains(dummyUserUUID))
            result.data.results.map { x =>
              if (x.id == dummyUserUUID) {
                x.role should be(GroupMemberRole.Manager)
              }
            }
          }
        }
      }

      "追加したグループメンバーを削除できるか" in {
        session {
          signIn()
          val groupId = createGroup()
          val params = Map("d" -> compact(render(Seq(("userId" -> dummyUserUUID) ~ ("role" -> JInt(GroupMemberRole.Member))))))
          post("/api/groups/" + groupId + "/members", params) { checkStatus() }

          delete("/api/groups/" + groupId + "/members/" + dummyUserUUID) { checkStatus() }

          get("/api/groups/" + groupId + "/members") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[MemberSummary]]]
            assert(!result.data.results.map(_.id).contains(dummyUserUUID))
          }
        }
      }

      "追加したグループメンバーを削除後、削除したメンバーがグループにアクセスできるか" in {
        session {
          signIn()
          // 新規グループ作成
          val groupId = createGroup()
          // メンバー追加
          val params = Map("d" -> compact(render(Seq(("userId" -> dummyUserUUID) ~ ("role" -> JInt(GroupMemberRole.Member))))))
          post(s"/api/groups/${groupId}/members", params) { checkStatus() }

          // サインアウト
          post("/api/signout") { checkStatus() }

          // 追加したグループメンバーでサインイン
          post("/api/signin", dummyUserLoginParams) { checkStatus() }
          get(s"/api/groups/${groupId}/members") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[MemberSummary]]]
            assert(result.data.results.map(_.id).contains(dummyUserUUID))
          }

          // サインアウト
          post("/api/signout") { checkStatus() }

          // グループ管理者でサインイン
          signIn()

          delete(s"/api/groups/${groupId}/members/${dummyUserUUID}") { checkStatus() }

          get(s"/api/groups/${groupId}/members") {
            checkStatus()
            val result = parse(body).extract[AjaxResponse[RangeSlice[MemberSummary]]]
            assert(!result.data.results.map(_.id).contains(dummyUserUUID))
          }

          // サインアウト
          post("/api/signout") { checkStatus() }

          // 追加したグループメンバーでサインイン
          post("/api/signin", dummyUserLoginParams) { checkStatus() }

          // グループ削除後であっても、グループIDを知っていればアクセスできることを確認
          get(s"/api/groups/${groupId}") {
            checkStatus()
          }

          get(s"/api/groups/${groupId}/members") {
            checkStatus()
          }
        }
      }

      "PUT /api/groups/:group_id/members/:user_id" - {
        "マネージャが1人から0人に変更される場合" in {
          session {
            signIn()
            val groupId = createGroup()
            val userId = "023bfa40-e897-4dad-96db-9fd3cf001e79"
            val params = Map("d" -> compact(render(("role" -> JInt(GroupMemberRole.Member)))))
            put(s"/api/groups/${groupId}/members/${userId}", params) {
              status should be(400)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("BadRequest")
            }
          }
        }

        "マネージャが2人から1人に変更される場合" in {
          session {
            signIn()
            val groupId = createGroup()
            // マネージャ追加1人
            val addParams = Map("d" -> compact(render(Seq(("userId" -> "cc130a5e-cb93-4ec2-80f6-78fa83f9bd04") ~ ("role" -> JInt(GroupMemberRole.Manager))))))
            post(s"/api/groups/${groupId}/members", addParams) {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("OK")
            }
            val userId = "023bfa40-e897-4dad-96db-9fd3cf001e79"
            // マネージャ除去1人
            val params = Map("d" -> compact(render(("role" -> JInt(GroupMemberRole.Member)))))
            put(s"/api/groups/${groupId}/members/${userId}", params) {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("OK")
            }
          }
        }
      }

      "DELETE /api/groups/:group_id/members/:user_id" - {
        "マネージャが一人もいなくなる場合" in {
          session {
            signIn()
            val groupId = createGroup()
            val userId = "023bfa40-e897-4dad-96db-9fd3cf001e79"
            delete(s"/api/groups/${groupId}/members/${userId}") {
              status should be(400)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("BadRequest")
            }
          }
        }

        "マネージャが一人残る場合" in {
          session {
            signIn()
            val groupId = createGroup()
            // マネージャ追加1人
            val addParams = Map("d" -> compact(render(Seq(("userId" -> "cc130a5e-cb93-4ec2-80f6-78fa83f9bd04") ~ ("role" -> JInt(GroupMemberRole.Manager))))))
            post(s"/api/groups/${groupId}/members", addParams) {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("OK")
            }
            val userId = "023bfa40-e897-4dad-96db-9fd3cf001e79"
            // マネージャ除去1人
            delete(s"/api/groups/${groupId}/members/${userId}") {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("OK")
            }
          }
        }
      }

      "GET /api/groups/:group_id/images" - {
        "GuestUser" in {
          session {
            signIn()
            val groupId = createGroup()
            post("/api/signout") { checkStatus() }
            get(s"/api/groups/${groupId}/images") {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("OK")
            }
          }
        }
        "未所属LoginUser" in {
          session {
            signIn()
            val groupId = createGroup()
            post("/api/signout") { checkStatus() }
            post("/api/signin", dummyUserLoginParams) { checkStatus() }
            get(s"/api/groups/${groupId}/images") {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("OK")
            }
          }
        }
        "MemberのLoginUser" in {
          session {
            signIn()
            val groupId = createGroup()
            // メンバー追加1人
            val addParams = Map("d" -> compact(render(Seq(("userId" -> dummyUserUUID) ~ ("role" -> JInt(GroupMemberRole.Member))))))
            post(s"/api/groups/${groupId}/members", addParams) {
              checkStatus()
            }
            post("/api/signout") { checkStatus() }
            post("/api/signin", dummyUserLoginParams) { checkStatus() }
            get(s"/api/groups/${groupId}/images") {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("OK")
            }
          }
        }
        "ManagerのLoginUser" in {
          session {
            signIn()
            val groupId = createGroup()
            // メンバー追加1人
            val addParams = Map("d" -> compact(render(Seq(("userId" -> dummyUserUUID) ~ ("role" -> JInt(GroupMemberRole.Manager))))))
            post(s"/api/groups/${groupId}/members", addParams) {
              checkStatus()
            }
            post("/api/signout") { checkStatus() }
            post("/api/signin", dummyUserLoginParams) { checkStatus() }
            get(s"/api/groups/${groupId}/images") {
              status should be(200)
              val result = parse(body).extract[AjaxResponse[Any]]
              result.status should be("OK")
            }
          }
        }
      }
    }
  }

  private def createGroup(): String = {
    val groupName = "groupName" + UUID.randomUUID.toString
    val params = Map("d" -> compact(render(("name" -> groupName) ~ ("description" -> "groupDescription"))))
    post("/api/groups", params) {
      checkStatus()
      parse(body).extract[AjaxResponse[Group]].data.id
    }
  }
}
