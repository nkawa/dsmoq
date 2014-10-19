package api

import java.io.File
import java.util.UUID

import _root_.api.api.logic.SpecCommonLogic
import dsmoq.AppConf
import dsmoq.controllers.{AjaxResponse, ApiController}
import dsmoq.persistence._
import dsmoq.services.json.DatasetData.{DatasetsSummary, Dataset}
import dsmoq.services.json.GroupData.Group
import dsmoq.services.json.RangeSlice
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, Formats}
import org.scalatest.{BeforeAndAfterEach, BeforeAndAfter, FreeSpec}
import org.scalatra.servlet.MultipartConfig
import org.scalatra.test.scalatest.ScalatraSuite
import scalikejdbc.config.{DBsWithEnv, DBs}
import scalikejdbc._, SQLInterpolation._

class DatasetListAuthorizationSpec extends FreeSpec with ScalatraSuite with BeforeAndAfter {
  protected implicit val jsonFormats: Formats = DefaultFormats

  private val dummyFile = new File("README.md")
  private val accesscCheckUserID = "eb7a596d-e50c-483f-bbc7-50019eea64d7" // dummy 4
  private val accessCheckUserLoginParams = Map("id" -> "dummy4", "password" -> "password")
  private val noAuthorityUserLoginParams = Map("id" -> "dummy2", "password" -> "password")
  private val dataCreateUser1ID = "023bfa40-e897-4dad-96db-9fd3cf001e79"  // dummy1
  private val dataCreateUser2ID = "4aaefd45-2fe5-4ce0-b156-3141613f69a6"  // dummy3

  // multi-part file upload config
  val holder = addServlet(classOf[ApiController], "/api/*")
  holder.getRegistration.setMultipartConfig(
    MultipartConfig(
      maxFileSize = Some(3 * 1024 * 1024),
      fileSizeThreshold = Some(1 * 1024 * 1024)
    ).toMultipartConfigElement
  )

  override def beforeAll() {
    super.beforeAll()
    DBsWithEnv("test").setup()
    System.setProperty(org.scalatra.EnvironmentKey, "test")
  }

  override def afterAll() {
    DBsWithEnv("test").close()
    super.afterAll()
  }

  before {
    SpecCommonLogic.insertDummyData()
  }

  after {
    SpecCommonLogic.deleteAllCreateData()
  }

  "Authorization Test" - {
    "設定した権限にあわせてデータセット一覧を取得できるか" in {
      session {
        signInDataCreateUser1()

        // データセットを作成
        val userAccessLevels = List(UserAccessLevel.Deny, UserAccessLevel.LimitedRead, UserAccessLevel.FullPublic, UserAccessLevel.Owner)
        val groupAccessLevels = List(GroupAccessLevel.Deny, GroupAccessLevel.LimitedPublic, GroupAccessLevel.FullPublic, GroupAccessLevel.Provider)
        val guestAccessLevels = List(DefaultAccessLevel.Deny, DefaultAccessLevel.LimitedPublic, DefaultAccessLevel.FullPublic)
        val files = Map("file[]" -> dummyFile)
        val datasetTuples = userAccessLevels.map { userAccessLevel =>
          groupAccessLevels.map { groupAccessLevel =>
            guestAccessLevels.map { guestAccessLevel =>
              // グループ作成/メンバー追加
              val groupId = createGroup()
              val memberParams = List("id[]" -> accesscCheckUserID, "role[]" -> GroupMemberRole.Member.toString)
              post("/api/groups/" + groupId + "/members", memberParams) {
                checkStatus()
              }

              post("/api/datasets", Map.empty, files) {
                checkStatus()
                val datasetId = parse(body).extract[AjaxResponse[Dataset]].data.id

                // アクセスレベル設定(ユーザー/グループ)
                val accessLevelParams = List(
                  "id[]" -> accesscCheckUserID, "type[]" -> OwnerType.User.toString, "accessLevel[]" -> userAccessLevel.toString,
                  "id[]" -> groupId, "type[]" -> OwnerType.Group.toString, "accessLevel[]" -> groupAccessLevel.toString
                )
                post("/api/datasets/" + datasetId + "/acl", accessLevelParams) {
                  checkStatus()
                }

                // ゲストアクセスレベル設定
                val guestAccessLevelParams = Map("accessLevel" -> guestAccessLevel.toString)
                put("/api/datasets/" + datasetId + "/guest_access", guestAccessLevelParams) {
                  checkStatus()
                }

                (datasetId, userAccessLevel, groupAccessLevel, guestAccessLevel)
              }
            }
          }
        }.flatten.flatten

        // ダミーユーザー時のデータセット一覧表示 権限が与えられているものすべて閲覧可能
        val params = Map("limit" -> "100")
        post("/api/signout") { checkStatus() }
        post("/api/signin", accessCheckUserLoginParams) { checkStatus() }
        get("/api/datasets", params) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
          val filteredDatasetTuples = datasetTuples.filter(x => x._2 > UserAccessLevel.Deny || x._3 > GroupAccessLevel.Deny || x._4 > DefaultAccessLevel.Deny)
          result.data.summary.total should be(filteredDatasetTuples.size)
          // datasetIdのfilter
          val datasetIds = filteredDatasetTuples.map(_._1).sorted
          assert(datasetIds.sameElements(result.data.results.map(_.id).sorted))
        }

        // ゲストアクセス時のデータセット一覧表示 ゲスト権限が与えられているもののみ閲覧可能
        post("/api/signout") { checkStatus() }
        get("/api/datasets", params) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
          val filteredDatasetTuples = datasetTuples.filter(_._4 > DefaultAccessLevel.Deny)
          result.data.summary.total should be(filteredDatasetTuples.size)
          // datasetIdのfilter
          val datasetIds = filteredDatasetTuples.map(_._1).sorted
          assert(datasetIds.sameElements(result.data.results.map(_.id).sorted))
        }

        // 何も権限を付与していないユーザーのデータセット一覧表示 ゲストと同じアクセス制限となる
        post("/api/signin", noAuthorityUserLoginParams) { checkStatus() }
        get("/api/datasets", params) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
          val filteredDatasetTuples = datasetTuples.filter(_._4 > DefaultAccessLevel.Deny)
          result.data.summary.total should be(filteredDatasetTuples.size)
          // datasetIdのfilter
          val datasetIds = filteredDatasetTuples.map(_._1).sorted
          assert(datasetIds.sameElements(result.data.results.map(_.id).sorted))
        }
      }
    }

    "ownerを指定してデータセットの絞り込みができるか" in {
      session {
        signInDataCreateUser1()

        // グループ作成/メンバー追加
        val groupId = createGroup()
        val memberParams = List("id[]" -> accesscCheckUserID, "role[]" -> GroupMemberRole.Member.toString)
        post("/api/groups/" + groupId + "/members", memberParams) {
          checkStatus()
        }

        // データセットを作成
        val userAccessLevels = List(UserAccessLevel.Deny, UserAccessLevel.LimitedRead, UserAccessLevel.FullPublic, UserAccessLevel.Owner)
        val groupAccessLevels = List(GroupAccessLevel.Deny, GroupAccessLevel.LimitedPublic, GroupAccessLevel.FullPublic, GroupAccessLevel.Provider)
        val guestAccessLevels = List(DefaultAccessLevel.Deny, DefaultAccessLevel.LimitedPublic, DefaultAccessLevel.FullPublic)
        val datasetTuples1 = userAccessLevels.map { userAccessLevel =>
          groupAccessLevels.map { groupAccessLevel =>
            guestAccessLevels.map { guestAccessLevel =>
              createDataset(groupId, userAccessLevel, groupAccessLevel, guestAccessLevel)
            }
          }
        }.flatten.flatten

        // 別ユーザーでデータセット作成
        post("/api/signout") { checkStatus() }
        signInDataCreateUser2()
        val datasetTuples2 = userAccessLevels.map { userAccessLevel =>
          groupAccessLevels.map { groupAccessLevel =>
            guestAccessLevels.map { guestAccessLevel =>
              createDataset(groupId, userAccessLevel, groupAccessLevel, guestAccessLevel)
            }
          }
        }.flatten.flatten

        // ダミーユーザー時のデータセット一覧表示 権限が与えられているものすべて閲覧可能
        val ownerParams = Map("limit" -> "100", "owner" -> dataCreateUser1ID)
        post("/api/signout") { checkStatus() }
        post("/api/signin", accessCheckUserLoginParams) { checkStatus() }
        get("/api/datasets", ownerParams) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
          val filteredDatasetTuples = datasetTuples1.filter(x => x._2 > UserAccessLevel.Deny || x._3 > GroupAccessLevel.Deny || x._4 > DefaultAccessLevel.Deny)
          result.data.summary.total should be(filteredDatasetTuples.size)
          // datasetIdのfilter
          val datasetIds = filteredDatasetTuples.map(_._1).sorted
          assert(datasetIds.sameElements(result.data.results.map(_.id).sorted))
        }
        val anotherOwnerParams = Map("limit" -> "100", "owner" -> dataCreateUser2ID)
        get("/api/datasets", anotherOwnerParams) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
          val filteredDatasetTuples = datasetTuples2.filter(x => x._2 > UserAccessLevel.Deny || x._3 > GroupAccessLevel.Deny || x._4 > DefaultAccessLevel.Deny)
          result.data.summary.total should be(filteredDatasetTuples.size)
          // datasetIdのfilter
          val datasetIds = filteredDatasetTuples.map(_._1).sorted
          assert(datasetIds.sameElements(result.data.results.map(_.id).sorted))
        }

        // ゲストユーザー時のデータセット一覧表示 閲覧権限があるのものすべて
        post("/api/signout") { checkStatus() }
        get("/api/datasets", ownerParams) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
          val filteredDatasetTuples = datasetTuples1.filter(x => x._4 > DefaultAccessLevel.Deny)
          result.data.summary.total should be(filteredDatasetTuples.size)
          // datasetIdのfilter
          val datasetIds = filteredDatasetTuples.map(_._1).sorted
          assert(datasetIds.sameElements(result.data.results.map(_.id).sorted))
        }
        get("/api/datasets", anotherOwnerParams) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
          val filteredDatasetTuples = datasetTuples2.filter(x => x._4 > DefaultAccessLevel.Deny)
          result.data.summary.total should be(filteredDatasetTuples.size)
          // datasetIdのfilter
          val datasetIds = filteredDatasetTuples.map(_._1).sorted
          assert(datasetIds.sameElements(result.data.results.map(_.id).sorted))
        }

        // 何も権限を付与していないユーザーのデータセット一覧表示 ゲストと同じアクセス制限となる
        post("/api/signin", noAuthorityUserLoginParams) { checkStatus() }
        get("/api/datasets", ownerParams) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
          val filteredDatasetTuples = datasetTuples1.filter(x => x._4 > DefaultAccessLevel.Deny)
          result.data.summary.total should be(filteredDatasetTuples.size)
          // datasetIdのfilter
          val datasetIds = filteredDatasetTuples.map(_._1).sorted
          assert(datasetIds.sameElements(result.data.results.map(_.id).sorted))
        }
        get("/api/datasets", anotherOwnerParams) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
          val filteredDatasetTuples = datasetTuples2.filter(x => x._4 > DefaultAccessLevel.Deny)
          result.data.summary.total should be(filteredDatasetTuples.size)
          // datasetIdのfilter
          val datasetIds = filteredDatasetTuples.map(_._1).sorted
          assert(datasetIds.sameElements(result.data.results.map(_.id).sorted))
        }
      }
    }

    "groupを指定してデータセットの絞り込みができるか" in {
      session {
        signInDataCreateUser1()

        // グループ作成/メンバー追加
        val groupId = createGroup()
        val anotherGroupId = createGroup()
        val memberParams = List("id[]" -> accesscCheckUserID, "role[]" -> GroupMemberRole.Member.toString)
        post("/api/groups/" + groupId + "/members", memberParams) {
          checkStatus()
        }

        // データセットを作成
        val userAccessLevels = List(UserAccessLevel.Deny, UserAccessLevel.LimitedRead, UserAccessLevel.FullPublic, UserAccessLevel.Owner)
        val groupAccessLevels = List(GroupAccessLevel.Deny, GroupAccessLevel.LimitedPublic, GroupAccessLevel.FullPublic, GroupAccessLevel.Provider)
        val guestAccessLevels = List(DefaultAccessLevel.Deny, DefaultAccessLevel.LimitedPublic, DefaultAccessLevel.FullPublic)
        val datasetTuples1 = userAccessLevels.map { userAccessLevel =>
          groupAccessLevels.map { groupAccessLevel =>
            guestAccessLevels.map { guestAccessLevel =>
              createDataset(groupId, userAccessLevel, groupAccessLevel, guestAccessLevel)
            }
          }
        }.flatten.flatten

        // 別グループにデータセット作成
        val datasetTuples2 = userAccessLevels.map { userAccessLevel =>
          groupAccessLevels.map { groupAccessLevel =>
            guestAccessLevels.map { guestAccessLevel =>
              createDataset(anotherGroupId, userAccessLevel, groupAccessLevel, guestAccessLevel)
            }
          }
        }.flatten.flatten

        // ダミーユーザー時のデータセット一覧表示 権限が与えられているものすべて閲覧可能
        val groupParams = Map("limit" -> "100", "group" -> groupId)
        post("/api/signout") { checkStatus() }
        post("/api/signin", accessCheckUserLoginParams) { checkStatus() }
        get("/api/datasets", groupParams) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
          // グループのアクセス権0以外
          val filteredDatasetTuples = datasetTuples1.filter(x => x._3 > GroupAccessLevel.Deny)
          result.data.summary.total should be(filteredDatasetTuples.size)
          // datasetIdのfilter
          val datasetIds = filteredDatasetTuples.map(_._1).sorted
          assert(datasetIds.sameElements(result.data.results.map(_.id).sorted))
        }
        val anotherGroupParams = Map("limit" -> "100", "group" -> anotherGroupId)
        get("/api/datasets", anotherGroupParams) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
          // グループのアクセス権が3、かつユーザーまたはゲストのアクセス権が0でないもの
          val filteredDatasetTuples = datasetTuples2.filter(x => x._3 == GroupAccessLevel.Provider && (x._2 > UserAccessLevel.Deny || x._4 > DefaultAccessLevel.Deny))
          result.data.summary.total should be(filteredDatasetTuples.size)
          // datasetIdのfilter
          val datasetIds = filteredDatasetTuples.map(_._1).sorted
          assert(datasetIds.sameElements(result.data.results.map(_.id).sorted))
        }

        // ゲストユーザー時のデータセット一覧表示 ゲスト閲覧可かつグループに編集権限があるものすべて
        post("/api/signout") { checkStatus() }
        get("/api/datasets", groupParams) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
          // グループのアクセス権3、かつゲストのアクセス権が0でないもの
          val filteredDatasetTuples = datasetTuples1.filter(x => x._3 == GroupAccessLevel.Provider && x._4 > DefaultAccessLevel.Deny)
          result.data.summary.total should be(filteredDatasetTuples.size)
          // datasetIdのfilter
          val datasetIds = filteredDatasetTuples.map(_._1).sorted
          assert(datasetIds.sameElements(result.data.results.map(_.id).sorted))
        }
        get("/api/datasets", anotherGroupParams) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
          // グループのアクセス権3、かつゲストのアクセス権が0でないもの
          val filteredDatasetTuples = datasetTuples2.filter(x => x._3 == GroupAccessLevel.Provider && x._4 > DefaultAccessLevel.Deny)
          result.data.summary.total should be(filteredDatasetTuples.size)
          // datasetIdのfilter
          val datasetIds = filteredDatasetTuples.map(_._1).sorted
          assert(datasetIds.sameElements(result.data.results.map(_.id).sorted))
        }

        // 何も権限を付与していないユーザーのデータセット一覧表示 ゲストと同じアクセス制限となる
        post("/api/signin", noAuthorityUserLoginParams) { checkStatus() }
        get("/api/datasets", groupParams) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
          // グループのアクセス権3、かつゲストのアクセス権が0でないもの
          val filteredDatasetTuples = datasetTuples1.filter(x => x._3 == GroupAccessLevel.Provider && x._4 > DefaultAccessLevel.Deny)
          result.data.summary.total should be(filteredDatasetTuples.size)
          // datasetIdのfilter
          val datasetIds = filteredDatasetTuples.map(_._1).sorted
          assert(datasetIds.sameElements(result.data.results.map(_.id).sorted))
        }
        get("/api/datasets", anotherGroupParams) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
          // グループのアクセス権3、かつゲストのアクセス権が0でないもの
          val filteredDatasetTuples = datasetTuples2.filter(x => x._3 == GroupAccessLevel.Provider && x._4 > DefaultAccessLevel.Deny)
          result.data.summary.total should be(filteredDatasetTuples.size)
          // datasetIdのfilter
          val datasetIds = filteredDatasetTuples.map(_._1).sorted
          assert(datasetIds.sameElements(result.data.results.map(_.id).sorted))
        }
      }
    }

    "ownerとgroupを指定してデータセットの絞り込みができるか" in {
      session {
        signInDataCreateUser1()

        // グループ作成/メンバー追加
        val groupId = createGroup()
        val anotherGroupId = createGroup()
        val memberParams = List("id[]" -> accesscCheckUserID, "role[]" -> GroupMemberRole.Member.toString)
        post("/api/groups/" + groupId + "/members", memberParams) {
          checkStatus()
        }

        // データセットを作成
        val userAccessLevels = List(UserAccessLevel.Deny, UserAccessLevel.LimitedRead, UserAccessLevel.FullPublic, UserAccessLevel.Owner)
        val groupAccessLevels = List(GroupAccessLevel.Deny, GroupAccessLevel.LimitedPublic, GroupAccessLevel.FullPublic, GroupAccessLevel.Provider)
        val guestAccessLevels = List(DefaultAccessLevel.Deny, DefaultAccessLevel.LimitedPublic, DefaultAccessLevel.FullPublic)
        val datasetTuples1 = userAccessLevels.map { userAccessLevel =>
          groupAccessLevels.map { groupAccessLevel =>
            guestAccessLevels.map { guestAccessLevel =>
              createDataset(groupId, userAccessLevel, groupAccessLevel, guestAccessLevel)
            }
          }
        }.flatten.flatten

        // 別グループにデータセット作成
        val datasetTuples2 = userAccessLevels.map { userAccessLevel =>
          groupAccessLevels.map { groupAccessLevel =>
            guestAccessLevels.map { guestAccessLevel =>
              createDataset(anotherGroupId, userAccessLevel, groupAccessLevel, guestAccessLevel)
            }
          }
        }.flatten.flatten

        // 別ユーザーでデータセット作成
        post("/api/signout") { checkStatus() }
        signInDataCreateUser2()
        val datasetTuples3 = userAccessLevels.map { userAccessLevel =>
          groupAccessLevels.map { groupAccessLevel =>
            guestAccessLevels.map { guestAccessLevel =>
              createDataset(groupId, userAccessLevel, groupAccessLevel, guestAccessLevel)
            }
          }
        }.flatten.flatten

        // 別ユーザー：別グループにデータセット作成
        val datasetTuples4 = userAccessLevels.map { userAccessLevel =>
          groupAccessLevels.map { groupAccessLevel =>
            guestAccessLevels.map { guestAccessLevel =>
              createDataset(anotherGroupId, userAccessLevel, groupAccessLevel, guestAccessLevel)
            }
          }
        }.flatten.flatten


        // ダミーユーザー時のデータセット一覧表示 権限が与えられているものすべて閲覧可能
        val params1 = Map("limit" -> "200", "owner" -> dataCreateUser1ID, "group" -> groupId)
        post("/api/signout") { checkStatus() }
        post("/api/signin", accessCheckUserLoginParams) { checkStatus() }
        get("/api/datasets", params1) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
          // グループのアクセス権0以外
          val filteredDatasetTuples = datasetTuples1.filter(x => x._3 > GroupAccessLevel.Deny)
          result.data.summary.total should be(filteredDatasetTuples.size)
          // datasetIdのfilter
          val datasetIds = filteredDatasetTuples.map(_._1).sorted
          assert(datasetIds.sameElements(result.data.results.map(_.id).sorted))
        }
        val params2 = Map("limit" -> "200", "owner" -> dataCreateUser1ID, "group" -> anotherGroupId)
        get("/api/datasets", params2) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
          // グループのアクセス権が3、かつユーザーまたはゲストのアクセス権が0でないもの
          val filteredDatasetTuples = datasetTuples2.filter(x => x._3 == GroupAccessLevel.Provider && (x._2 > UserAccessLevel.Deny || x._4 > DefaultAccessLevel.Deny))
          result.data.summary.total should be(filteredDatasetTuples.size)
          // datasetIdのfilter
          val datasetIds = filteredDatasetTuples.map(_._1).sorted
          assert(datasetIds.sameElements(result.data.results.map(_.id).sorted))
        }
        val params3 = Map("limit" -> "200", "owner" -> dataCreateUser2ID, "group" -> groupId)
        // 別ユーザーが作ったdatasetについても同等の結果となるか
        get("/api/datasets", params3) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
          // グループのアクセス権0以外
          val filteredDatasetTuples = datasetTuples3.filter(x => x._3 > GroupAccessLevel.Deny)
          result.data.summary.total should be(filteredDatasetTuples.size)
          // datasetIdのfilter
          val datasetIds = filteredDatasetTuples.map(_._1).sorted
          assert(datasetIds.sameElements(result.data.results.map(_.id).sorted))
        }
        val params4 = Map("limit" -> "200", "owner" -> dataCreateUser2ID, "group" -> anotherGroupId)
        get("/api/datasets", params4) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
          // グループのアクセス権が3、かつユーザーまたはゲストのアクセス権が0でないもの
          val filteredDatasetTuples = datasetTuples4.filter(x => x._3 == GroupAccessLevel.Provider && (x._2 > UserAccessLevel.Deny || x._4 > DefaultAccessLevel.Deny))
          result.data.summary.total should be(filteredDatasetTuples.size)
          // datasetIdのfilter
          val datasetIds = filteredDatasetTuples.map(_._1).sorted
          assert(datasetIds.sameElements(result.data.results.map(_.id).sorted))
        }

        // ゲストユーザー時のデータセット一覧表示 ゲスト閲覧可かつグループに編集権限があるものすべて
        post("/api/signout") { checkStatus() }
        get("/api/datasets", params1) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
          // グループのアクセス権0以外
          val filteredDatasetTuples = datasetTuples1.filter(x => x._3 == GroupAccessLevel.Provider && x._4 > DefaultAccessLevel.Deny)
          result.data.summary.total should be(filteredDatasetTuples.size)
          // datasetIdのfilter
          val datasetIds = filteredDatasetTuples.map(_._1).sorted
          assert(datasetIds.sameElements(result.data.results.map(_.id).sorted))
        }
        get("/api/datasets", params2) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
          // グループのアクセス権が3、かつユーザーまたはゲストのアクセス権が0でないもの
          val filteredDatasetTuples = datasetTuples2.filter(x => x._3 == GroupAccessLevel.Provider && x._4 > DefaultAccessLevel.Deny)
          result.data.summary.total should be(filteredDatasetTuples.size)
          // datasetIdのfilter
          val datasetIds = filteredDatasetTuples.map(_._1).sorted
          assert(datasetIds.sameElements(result.data.results.map(_.id).sorted))
        }
        // 別ユーザーが作ったdatasetについても同等の結果となるか
        get("/api/datasets", params3) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
          // グループのアクセス権0以外
          val filteredDatasetTuples = datasetTuples3.filter(x => x._3 == GroupAccessLevel.Provider && x._4 > DefaultAccessLevel.Deny)
          result.data.summary.total should be(filteredDatasetTuples.size)
          // datasetIdのfilter
          val datasetIds = filteredDatasetTuples.map(_._1).sorted
          assert(datasetIds.sameElements(result.data.results.map(_.id).sorted))
        }
        get("/api/datasets", params4) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
          // グループのアクセス権が3、かつユーザーまたはゲストのアクセス権が0でないもの
          val filteredDatasetTuples = datasetTuples4.filter(x => x._3 == GroupAccessLevel.Provider && x._4 > DefaultAccessLevel.Deny)
          result.data.summary.total should be(filteredDatasetTuples.size)
          // datasetIdのfilter
          val datasetIds = filteredDatasetTuples.map(_._1).sorted
          assert(datasetIds.sameElements(result.data.results.map(_.id).sorted))
        }

        // 何も権限を付与していないユーザーのデータセット一覧表示 ゲストと同じアクセス制限となる
        post("/api/signin", noAuthorityUserLoginParams) { checkStatus() }
        get("/api/datasets", params1) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
          // グループのアクセス権0以外
          val filteredDatasetTuples = datasetTuples1.filter(x => x._3 == GroupAccessLevel.Provider && x._4 > DefaultAccessLevel.Deny)
          result.data.summary.total should be(filteredDatasetTuples.size)
          // datasetIdのfilter
          val datasetIds = filteredDatasetTuples.map(_._1).sorted
          assert(datasetIds.sameElements(result.data.results.map(_.id).sorted))
        }
        get("/api/datasets", params2) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
          // グループのアクセス権が3、かつユーザーまたはゲストのアクセス権が0でないもの
          val filteredDatasetTuples = datasetTuples2.filter(x => x._3 == GroupAccessLevel.Provider && x._4 > DefaultAccessLevel.Deny)
          result.data.summary.total should be(filteredDatasetTuples.size)
          // datasetIdのfilter
          val datasetIds = filteredDatasetTuples.map(_._1).sorted
          assert(datasetIds.sameElements(result.data.results.map(_.id).sorted))
        }
        // 別ユーザーが作ったdatasetについても同等の結果となるか
        get("/api/datasets", params3) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
          // グループのアクセス権0以外
          val filteredDatasetTuples = datasetTuples3.filter(x => x._3 == GroupAccessLevel.Provider && x._4 > DefaultAccessLevel.Deny)
          result.data.summary.total should be(filteredDatasetTuples.size)
          // datasetIdのfilter
          val datasetIds = filteredDatasetTuples.map(_._1).sorted
          assert(datasetIds.sameElements(result.data.results.map(_.id).sorted))
        }
        get("/api/datasets", params4) {
          checkStatus()
          val result = parse(body).extract[AjaxResponse[RangeSlice[DatasetsSummary]]]
          // グループのアクセス権が3、かつユーザーまたはゲストのアクセス権が0でないもの
          val filteredDatasetTuples = datasetTuples4.filter(x => x._3 == GroupAccessLevel.Provider && x._4 > DefaultAccessLevel.Deny)
          result.data.summary.total should be(filteredDatasetTuples.size)
          // datasetIdのfilter
          val datasetIds = filteredDatasetTuples.map(_._1).sorted
          assert(datasetIds.sameElements(result.data.results.map(_.id).sorted))
        }
      }
    }
  }

  private def signInDataCreateUser1() {
    val params = Map("id" -> "dummy1", "password" -> "password")
    post("/api/signin", params) {
      checkStatus()
    }
  }

  private def signInDataCreateUser2() {
    val params = Map("id" -> "dummy3", "password" -> "password")
    post("/api/signin", params) {
      checkStatus()
    }
  }

  private def checkStatus() {
    status should be(200)
    val result = parse(body).extract[AjaxResponse[Any]]
    result.status should be("OK")
  }

  private def createGroup(): String = {
    val groupName = "groupName" + UUID.randomUUID.toString
    val params = Map("name" -> groupName, "description" -> "groupDescription")
    post("/api/groups", params) {
      checkStatus()
      parse(body).extract[AjaxResponse[Group]].data.id
    }
  }

  private def createDataset(groupId: String, userAccessLevel:Int, groupAccessLevel: Int, guestAccessLevel: Int) = {
    val files = Map("file[]" -> dummyFile)
    post("/api/datasets", Map.empty, files) {
      checkStatus()
      val datasetId = parse(body).extract[AjaxResponse[Dataset]].data.id

      // アクセスレベル設定(ユーザー/グループ)
      val accessLevelParams = List(
        "id[]" -> accesscCheckUserID, "type[]" -> OwnerType.User.toString, "accessLevel[]" -> userAccessLevel.toString,
        "id[]" -> groupId, "type[]" -> OwnerType.Group.toString, "accessLevel[]" -> groupAccessLevel.toString
      )
      post("/api/datasets/" + datasetId + "/acl", accessLevelParams) {
        checkStatus()
      }

      // ゲストアクセスレベル設定
      val guestAccessLevelParams = Map("accessLevel" -> guestAccessLevel.toString)
      put("/api/datasets/" + datasetId + "/guest_access", guestAccessLevelParams) {
        checkStatus()
      }
      (datasetId, userAccessLevel, groupAccessLevel, guestAccessLevel)
    }
  }
}
