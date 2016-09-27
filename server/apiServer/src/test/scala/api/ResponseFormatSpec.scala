package api

import java.io.File
import java.util.UUID

import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._

import common.DsmoqSpec
import dsmoq.AppConf
import dsmoq.controllers.AjaxResponse
import dsmoq.persistence.PostgresqlHelper._
import dsmoq.persistence._
import dsmoq.persistence.{ DefaultAccessLevel, OwnerType, UserAccessLevel, GroupAccessLevel }
import dsmoq.services.json.DatasetData.Dataset
import dsmoq.services.json.DatasetData.DatasetAddFiles
import dsmoq.services.json.DatasetData.DatasetAddImages
import dsmoq.services.json.DatasetData._
import dsmoq.services.json.GroupData.AddMembers
import dsmoq.services.json.GroupData.Group
import dsmoq.services.json.GroupData.GroupAddImages
import dsmoq.services.json.RangeSlice
import dsmoq.services.json.TaskData._
import scalikejdbc._

class ResponseFormatSpec extends DsmoqSpec {
  private val dummyFile = new File("../README.md")
  private val dummyImage = new File("../../client/www/dummy/images/nagoya.jpg")
  private val testUserName = "dummy1"
  private val dummyUserName = "dummy4"
  private val testUserId = "023bfa40-e897-4dad-96db-9fd3cf001e79" // dummy1
  private val dummyUserId = "eb7a596d-e50c-483f-bbc7-50019eea64d7" // dummy 4

  "API test" - {
    "dataset" - {
      "POST /api/datasets/:dataset_id/images" in {
        session {
          signIn()
          val datasetId = createDataset()
          val files = Map("images" -> dummyImage)
          post(s"/api/datasets/${datasetId}/images", Map.empty, files) {
            status should be(200)
            parse(body).extract[AjaxResponse[DatasetAddImages]]
          }
        }
      }
      "POST /api/groups/:group_id/images" in {
        session {
          signIn()
          val groupId = createGroup()
          val files = Map("images" -> dummyImage)
          post(s"/api/groups/${groupId}/images", Map.empty, files) {
            status should be(200)
            parse(body).extract[AjaxResponse[GroupAddImages]]
          }
        }
      }
      "PUT /api/datasets/:dataset_id/guest_access" in {
        session {
          signIn()
          val datasetId = createDataset()
          val params = Map("d" -> compact(render(("accessLevel" -> JInt(DefaultAccessLevel.FullPublic)))))
          put(s"/api/datasets/${datasetId}/guest_access", params) {
            status should be(200)
            parse(body).extract[AjaxResponse[DatasetGuestAccessLevel]]
          }
        }
      }
      "PUT /api/datasets/:dataset_id/metadata" in {
        session {
          signIn()
          val datasetId = createDataset()
          val params = Map(
            "d" -> compact(
              render(
                ("name" -> "変更後データセット") ~
                  ("description" -> "change description") ~
                  ("license" -> AppConf.defaultLicenseId)
              )
            )
          )
          put(s"/api/datasets/${datasetId}/metadata", params) {
            status should be(200)
            parse(body).extract[AjaxResponse[DatasetMetaData]]
          }
        }
      }
      "POST /api/groups/:groupId/members" in {
        session {
          signIn()
          val groupId = createGroup()
          val params = Map("d" -> compact(render(Seq(("userId" -> dummyUserId) ~ ("role" -> JInt(GroupMemberRole.Member))))))
          post(s"/api/groups/${groupId}/members", params) {
            status should be(200)
            parse(body).extract[AjaxResponse[AddMembers]]
          }
        }
      }
    }
  }

  /**
   * グループを作成します。
   * @return 作成したグループID
   */
  private def createGroup(): String = {
    val groupName = "groupName" + UUID.randomUUID.toString
    val params = Map("d" -> compact(render(("name" -> groupName) ~ ("description" -> "groupDescription"))))
    post("/api/groups", params) {
      checkStatus()
      parse(body).extract[AjaxResponse[Group]].data.id
    }
  }
}
