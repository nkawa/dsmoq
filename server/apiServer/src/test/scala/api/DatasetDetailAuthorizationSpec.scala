package api

import java.io.File
import java.util.UUID

import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._

import common.DsmoqSpec
import dsmoq.controllers.AjaxResponse
import dsmoq.persistence._
import dsmoq.services.json.DatasetData.Dataset
import dsmoq.services.json.GroupData.Group

class DatasetDetailAuthorizationSpec extends DsmoqSpec {
  private val dummyFile = new File("../README.md")
  private val dummyUserId = "eb7a596d-e50c-483f-bbc7-50019eea64d7" // dummy 4
  private val dummyUserLoginParams = Map("d" -> compact(render(("id" -> "dummy4") ~ ("password" -> "password"))))
  private val anotherUserLoginParams = Map("d" -> compact(render(("id" -> "dummy2") ~ ("password" -> "password"))))

  "Authorization Test" - {
    "設定した権限にあわせてデータセット詳細を閲覧できるか" in {
      session {
        signIn()

        // データセットを作成
        val userAccessLevels = Seq(UserAccessLevel.Deny, UserAccessLevel.LimitedRead, UserAccessLevel.FullPublic, UserAccessLevel.Owner)
        val groupAccessLevels = Seq(GroupAccessLevel.Deny, GroupAccessLevel.LimitedPublic, GroupAccessLevel.FullPublic, GroupAccessLevel.Provider)
        val guestAccessLevels = Seq(DefaultAccessLevel.Deny, DefaultAccessLevel.LimitedPublic, DefaultAccessLevel.FullPublic)
        val files = Map("file[]" -> dummyFile)
        val datasetParams = userAccessLevels.map { userAccessLevel =>
          guestAccessLevels.map { groupAccessLevel =>
            guestAccessLevels.map { guestAccessLevel =>
              // グループ作成/メンバー追加
              val groupId = createGroup()
              val memberParams = Map("d" -> compact(render(Seq(("userId" -> dummyUserId) ~ ("role" -> GroupMemberRole.Member)))))
              post("/api/groups/" + groupId + "/members", memberParams) {
                checkStatus()
              }

              val params = Map("saveLocal" -> "true", "saveS3" -> "false", "name" -> "test1")
              post("/api/datasets", params, files) {
                checkStatus()
                val datasetId = parse(body).extract[AjaxResponse[Dataset]].data.id

                // アクセスレベル設定(ユーザー/グループ)
                val accessLevelParams = Map("d" -> compact(render(Seq(
                  ("id" -> dummyUserId) ~ ("ownerType" -> JInt(OwnerType.User)) ~ ("accessLevel" -> JInt(userAccessLevel)),
                  ("id" -> groupId) ~ ("ownerType" -> JInt(OwnerType.Group)) ~ ("accessLevel" -> JInt(groupAccessLevel))
                ))))
                post("/api/datasets/" + datasetId + "/acl", accessLevelParams) {
                  checkStatus()
                }

                // ゲストアクセスレベル設定
                val guestAccessLevelParams = Map("d" -> compact(render(("accessLevel" -> JInt(guestAccessLevel)))))
                put("/api/datasets/" + datasetId + "/guest_access", guestAccessLevelParams) {
                  checkStatus()
                }

                (datasetId, userAccessLevel, groupAccessLevel, guestAccessLevel)
              }
            }
          }
        }.flatten.flatten

        // ダミーユーザー時のデータセット詳細閲覧 Denyではない(AllowLimitedRead以上)であれば閲覧可
        post("/api/signout") { checkStatus() }
        post("/api/signin", dummyUserLoginParams) { checkStatus() }
        datasetParams.foreach { params =>
          withClue("user params: " + params) {
            if (params._2 > UserAccessLevel.Deny || params._3 > GroupAccessLevel.Deny || params._4 > DefaultAccessLevel.Deny) {
              get("/api/datasets/" + params._1) {
                status should be(200)
                val result = parse(body).extract[AjaxResponse[Dataset]]
                result.data.id should be(params._1)
                val permission = Seq(params._2, params._3, params._4).sorted.last
                result.data.permission should be(permission)
                result.data.defaultAccessLevel should be(params._4)
              }
            } else {
              get("/api/datasets/" + params._1) {
                // AccessDenied 
                status should be(403)
                val result = parse(body).extract[AjaxResponse[Any]]
                result.status should be("AccessDenied")
              }
            }
          }
        }

        // ゲストアクセス時のデータセット詳細閲覧  Denyではない(AllowLimitedRead以上)であれば閲覧可
        post("/api/signout") { checkStatus() }
        datasetParams.foreach { params =>
          withClue("guest params: " + params) {
            if (params._4 > DefaultAccessLevel.Deny) {
              get("/api/datasets/" + params._1) {
                status should be(200)
                val result = parse(body).extract[AjaxResponse[Dataset]]
                result.data.id should be(params._1)
                result.data.permission should be(params._4)
                result.data.defaultAccessLevel should be(params._4)
              }
            } else {
              get("/api/datasets/" + params._1) {
                // AccessDenied 
                status should be(403)
                val result = parse(body).extract[AjaxResponse[Any]]
                result.status should be("AccessDenied")
              }
            }
          }
        }

        // 何も権限を付与していないユーザーのデータセット詳細閲覧 ゲストと同じアクセス制限となる
        post("/api/signin", anotherUserLoginParams) { checkStatus() }
        datasetParams.foreach { params =>
          withClue("not authorization user params: " + params) {
            if (params._4 > DefaultAccessLevel.Deny) {
              get("/api/datasets/" + params._1) {
                status should be(200)
                val result = parse(body).extract[AjaxResponse[Dataset]]
                result.data.id should be(params._1)
                result.data.permission should be(params._4)
                result.data.defaultAccessLevel should be(params._4)
              }
            } else {
              get("/api/datasets/" + params._1) {
                // AccessDenied 
                status should be(403)
                val result = parse(body).extract[AjaxResponse[Any]]
                result.status should be("AccessDenied")
              }
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
