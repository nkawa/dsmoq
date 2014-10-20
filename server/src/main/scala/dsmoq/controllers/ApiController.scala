package dsmoq.controllers

import org.json4s._
import org.json4s.jackson.JsonMethods
import org.scalatra.ScalatraServlet
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json.JacksonJsonSupport
import dsmoq.services._
import scala.util.{Success, Failure}
import org.scalatra.servlet.FileUploadSupport
import dsmoq.controllers.json._
import dsmoq.services.json.DatasetData._
import dsmoq.services.json.GroupData._
import dsmoq.forms._
import dsmoq.AppConf
import dsmoq.exceptions.{InputValidationException, NotFoundException, NotAuthorizedException}

class ApiController extends ScalatraServlet
    with JacksonJsonSupport with SessionTrait with FileUploadSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    contentType = formats("json")
  }

  get("/*") {
    throw new Exception("err")
  }

  // --------------------------------------------------------------------------
  // auth api
  // --------------------------------------------------------------------------
  post("/signin") {
    val json = params.get("d").map(x => {
      JsonMethods.parse(x).extract[SigninParams]
    }).getOrElse {
      SigninParams()
    }

    AccountService.findUserByIdAndPassword(json.id.getOrElse(""), json.password.getOrElse("")) match {
      case Success(x) =>
        setSignedInUser(x)
        AjaxResponse("OK", x)
      case Failure(e) =>
        clearSession()
        e match {
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse ("NG")
        }
    }
  }

  post("/signout") {
    clearSession()
    AjaxResponse("OK", guestUser)
  }

  // --------------------------------------------------------------------------
  // profile api
  // --------------------------------------------------------------------------
  get("/profile") {
    AjaxResponse("OK", currentUser)
  }

  post("/profile") {
    val json = params.get("d").map(JsonMethods.parse(_).extract[UpdateProfileParams])
                               .getOrElse(UpdateProfileParams())
    (for {
      user <- signedInUser
      userNew <- AccountService.updateUserProfile(user.id, json.name, json.fullname,json.organization,
                                                  json.title, json.description, json.image)
    } yield {
      setSignedInUser(userNew)
      userNew
    }) match {
      case Success(x) =>
        AjaxResponse("OK", x)
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  post("/profile/email_change_requests") {
    val json = params.get("d").map(JsonMethods.parse(_).extract[UpdateMailAddressParams])
                               .getOrElse(UpdateMailAddressParams())
    (for {
      user <- signedInUser
      userNew <- AccountService.changeUserEmail(user.id, json.email)
    } yield {
      setSignedInUser(userNew)
      userNew
    }) match {
      case Success(x) =>
        AjaxResponse("OK", x)
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  put("/profile/password") {
    val json = params.get("d").map(JsonMethods.parse(_).extract[UpdatePasswordParams])
                               .getOrElse(UpdatePasswordParams())
    (for {
      user <- signedInUser
      _ <- AccountService.changeUserPassword(user.id, json.currentPassword, json.newPassword)
    } yield {}) match {
      case Success(_) =>
        AjaxResponse("OK")
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  // --------------------------------------------------------------------------
  // dataset api
  // --------------------------------------------------------------------------
  post("/datasets") {
    val files = fileMultiParams("files")
    (for {
      user <- signedInUser
      dataset <- DatasetService.create(files, user)
    } yield dataset) match {
      case Success(x) =>
        AjaxResponse("OK", x)
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  get("/datasets") {
    val json = params.get("d").map(JsonMethods.parse(_).extract[SearchDatasetsParams])
                               .getOrElse(SearchDatasetsParams())
    DatasetService.search(json.query, json.owners, json.groups, json.attributes,
                          json.limit, json.offset, currentUser) match {
      case Success(x) =>
        AjaxResponse("OK", x)
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case e =>
            log(e.getMessage, e)
            AjaxResponse("NG")
        }
    }
  }

  get("/datasets/:datasetId") {
    val id = params("datasetId")
    DatasetService.get(id, currentUser) match {
      case Success(x) =>
        AjaxResponse("OK", x)
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case e =>
            log(e.getMessage, e)
            AjaxResponse("NG")
        }
    }
  }

  post("/datasets/:datasetId/files") {
    val id = params("datasetId")
    val files = fileMultiParams("files")
    (for {
      user <- signedInUser
      datasets <- DatasetService.addFiles(id, files, user)
    } yield datasets) match {
      case Success(x) =>
        AjaxResponse("OK", x)
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case e =>
            log(e.getMessage, e)
            AjaxResponse("NG")
        }
    }
  }

  post("/datasets/:datasetId/files/:fileId") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")
    val file = fileParams.get("file")

    (for {
      user <- signedInUser
      file <- DatasetService.updateFile(datasetId, fileId, file, user)
    } yield file) match {
      case Success(x) =>
        AjaxResponse("OK", x)
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  put("/datasets/:datasetId/files/:fileId/metadata") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")
    val json = params.get("d").map(JsonMethods.parse(_).extract[ModifyDatasetFileMetadataParams])
                               .getOrElse(ModifyDatasetFileMetadataParams())
    (for {
      user <- signedInUser
      file <- DatasetService.updateFileMetadata(datasetId, fileId, json.name.getOrElse(""), json.description.getOrElse(""), user)
    } yield file) match {
      case Success(x) =>
        AjaxResponse("OK", x)
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  delete("/datasets/:datasetId/files/:fileId") {
    val datasetId = params("datasetId")
    val fileId = params("fileId")

    (for {
      user <- signedInUser
      file <- DatasetService.deleteDatasetFile(datasetId, fileId, user)
    } yield file) match {
      case Success(x) =>
        AjaxResponse("OK", x)
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  put("/datasets/:datasetId/metadata") {
    val datasetId = params("datasetId")
    val json = params.get("d").map(JsonMethods.parse(_).extract[ModifyDatasetMetaParams])
                               .getOrElse(ModifyDatasetMetaParams())
    (for {
      user <- signedInUser
      result <- DatasetService.modifyDatasetMeta(datasetId, json.name, json.description, json.license, json.attributes, user)
    } yield {}) match {
      case Success(_) => AjaxResponse("OK")
      case Failure(e) =>
        println(e)
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  post("/datasets/:datasetId/images") {
    val datasetId = params("datasetId")
    val images = fileMultiParams.get("images").getOrElse(Seq.empty)
    (for {
      user <- signedInUser
      images <- DatasetService.addImages(datasetId, images, user)
    } yield images) match {
      case Success(x) =>
        AjaxResponse("OK", x)
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  put("/datasets/:datasetId/images/primary") {
    val datasetId = params("datasetId")
    val json = params.get("d").map(JsonMethods.parse(_).extract[ChangePrimaryImageParams])
                               .getOrElse(ChangePrimaryImageParams())
    (for {
      user <- signedInUser
      result <- DatasetService.changePrimaryImage(datasetId, json.imageId.getOrElse(""), user)
    } yield result) match {
      case Success(_) =>
        AjaxResponse("OK")
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  delete("/datasets/:datasetId/images/:imageId") {
    val datasetId = params("datasetId")
    val json = params.get("d").map(JsonMethods.parse(_).extract[DeleteImageParams])
                               .getOrElse(DeleteImageParams())
    (for {
      user <- signedInUser
      primaryImage <- DatasetService.deleteImage(datasetId, json.imageId.getOrElse(""), user)
    } yield primaryImage) match {
      case Success(x) =>
        AjaxResponse("OK", x)
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case _ => AjaxResponse("NG")
        }
    }
  }

  post("/datasets/:datasetId/acl") {
    val datasetId = params("datasetId")
    val acl = params.get("d").map(JsonMethods.parse(_).extract[List[DataSetAccessControlItem]])
                               .getOrElse(List.empty)
    (for {
      user <- signedInUser
      result <- DatasetService.setAccessControl(datasetId, acl, user)
    } yield result) match {
      case Success(x) =>
        AjaxResponse("OK", x)
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  put("/datasets/:datasetId/guest_access") {
    val datasetId = params("datasetId")
    val json = params.get("d").map(JsonMethods.parse(_).extract[UpdateDatasetGuestAccessParams])
                               .getOrElse(UpdateDatasetGuestAccessParams())
    (for {
      user <- signedInUser
      result <- DatasetService.setGuestAccessLevel(datasetId, json.accessLevel.getOrElse(0), user)
    } yield result) match {
      case Success(_) =>
        AjaxResponse("OK")
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  delete("/datasets/:datasetId") {
    val datasetId = params("datasetId")

    (for {
      user <- signedInUser
      result = DatasetService.deleteDataset(datasetId, user)
    } yield {}) match {
      case Success(x) =>
        AjaxResponse("OK")
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case _ => AjaxResponse("NG")
        }
    }
  }

  // --------------------------------------------------------------------------
  // group api
  // --------------------------------------------------------------------------
  get("/groups") {
    val data = params.get("d").map(x => {
      JsonMethods.parse(x).extract[SearchGroupsParams]
    }).getOrElse {
      SearchGroupsParams()
    }

    GroupService.search(data, currentUser) match {
      case Success(x) =>
        AjaxResponse("OK", x)
      case Failure(e) =>
        e match {
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  get("/groups/:groupId") {
    val groupId = params("groupId")
    GroupService.get(GetGroupParams(currentUser, groupId)) match {
      case Success(x) => AjaxResponse("OK", x)
      case Failure(e) =>
        e match {
          case e: NotFoundException => AjaxResponse("NotFound")
          case _ => AjaxResponse("NG")
        }
    }
  }

  get("/groups/:groupId/members") {
    val groupId = params("groupId")
    val json = params.get("d").map(x => JsonMethods.parse(x).extract[GetGroupMembersParams])
                               .getOrElse(GetGroupMembersParams())
    GroupService.getGroupMembers(groupId, json.limit, json.offset, currentUser) match {
      case Success(x) =>
        AjaxResponse("OK", x)
      case Failure(e) =>
        e match {
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  post("/groups") {
    val name = params.get("name")
    val description = params.get("description")

    if (!isValidSession()) halt(body = AjaxResponse("Unauthorized"))

    val response = for {
      user <- signedInUser
      facadeParams = CreateGroupParams(user, name, description)
      group <- GroupService.createGroup(facadeParams)
    } yield {
      AjaxResponse("OK", group)
    }
    response match {
      case Success(x) => x
      case Failure(e) =>
        e match {
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  put("/groups/:groupId") {
    val groupId = params("groupId")
    val name = params.get("name")
    val description = params.get("description")

    val response = for {
      user <- signedInUser
      facadeParams = ModifyGroupParams(user, groupId, name, description)
      group <- GroupService.modifyGroup(facadeParams)
    } yield {
      AjaxResponse("OK", group)
    }
    response match {
      case Success(x) => x
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  post("/groups/:groupId/images") {
    val groupId = params("groupId")
    val images = fileMultiParams.get("images")

    val response = for {
      user <- signedInUser
      facadeParams = AddImagesToGroupParams(user, groupId, images)
      files <- GroupService.addImages(facadeParams)
    } yield {
      AjaxResponse("OK", files)
    }
    response match {
      case Success(x) => x
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  put("/groups/:groupId/images/primary") {
    val groupId = params("groupId")
    val id = params.get("id")

    val response = for {
      user <- signedInUser
      facadeParams = ChangeGroupPrimaryImageParams(user, id, groupId)
      result <- GroupService.changePrimaryImage(facadeParams)
    } yield {
      result
    }
    response match {
      case Success(x) => AjaxResponse("OK")
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  delete("/groups/:groupId/images/:imageId") {
    val groupId = params("groupId")
    val imageId = params("imageId")

    val response = for {
      user <- signedInUser
      facadeParams = DeleteGroupImageParams(user, imageId, groupId)
      primaryImage <- GroupService.deleteImage(facadeParams)
    } yield {
      AjaxResponse("OK", primaryImage)
    }
    response match {
      case Success(x) => x
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case _ => AjaxResponse("NG")
        }
    }
  }

  post("/groups/:groupId/members") {
    val groupId = params("groupId")
    val userIds = multiParams.get("id[]")
    val roles = multiParams.get("role[]")

    val response = for {
      user <- signedInUser
      facadeParams = SetUserRoleParams(user, groupId, userIds, roles)
      userIds <- GroupService.setUserRole(facadeParams)
    } yield {
      userIds
    }
    response match {
      case Success(x) => AjaxResponse("OK")
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case e: InputValidationException => AjaxResponse("BadRequest", e.getErrorMessage())
          case _ => AjaxResponse("NG")
        }
    }
  }

  delete("/groups/:groupId") {
    val groupId = params("groupId")

    val response = for {
      user <- signedInUser
      facadeParams = DeleteGroupParams(user, groupId)
      result <- GroupService.deleteGroup(facadeParams)
    } yield {
      result
    }
    response match {
      case Success(x) => AjaxResponse("OK")
      case Failure(e) =>
        e match {
          case e: NotAuthorizedException => AjaxResponse("Unauthorized")
          case e: NotFoundException => AjaxResponse("NotFound")
          case _ => AjaxResponse("NG")
        }
    }
  }

  // --------------------------------------------------------------------------
  get("/system/is_valid_email") {
    // TODO not implemented
    AjaxResponse("OK")
  }

  get("/licenses") {
    val licenses = SystemService.getLicenses()
    AjaxResponse("OK", licenses)
  }

  get("/accounts") {
    val accounts = SystemService.getAccounts()
    AjaxResponse("OK", accounts)
  }

  get("/suggests/users_and_groups") {
    val query = params.get("query")
    val result = SystemService.getUsersAndGroups(query)
    AjaxResponse("OK", result)
  }

  get("/suggests/attributes") {
    val query = params.get("query")
    val attributes = SystemService.getAttributes(query)
    AjaxResponse("OK", attributes)
  }

  get("/suggests/groups") {
    val query = params.get("query")
    val groups = SystemService.getGroups(query)
    AjaxResponse("OK", groups)
  }
}

case class AjaxResponse[A](status: String, data: A = {})
