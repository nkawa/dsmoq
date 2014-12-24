package api.api.logic

import java.io.File

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import org.joda.time.DateTime
import scalikejdbc._, SQLInterpolation._
import dsmoq._
import dsmoq.persistence.PostgresqlHelper._
import scala.collection.JavaConversions._

object SpecCommonLogic {
  private val defaultUserIconId = "8a981652-ea4d-48cf-94db-0ceca7d81aef"
  private val presetImageIds = List(
    "8a981652-ea4d-48cf-94db-0ceca7d81aef",
    "8b570468-9814-4d30-8c04-392b263b6404",
    "960a5601-2b60-2531-e6ad-54b91612ede5"
  )
  private lazy val cre = new BasicAWSCredentials(AppConf.s3AccessKey, AppConf.s3SecretKey)
  private lazy val client = new AmazonS3Client(cre)

  def insertDummyData() {
    val ts = DateTime.now
    // user1 create
    DB localTx { implicit s =>
      persistence.User.create(
        id = "023bfa40-e897-4dad-96db-9fd3cf001e79",
        name = "dummy1",
        fullname = "テストダミーユーザー1",
        organization = "organization 1",
        title = "title 1",
        description = "description 1",
        imageId = defaultUserIconId,
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      persistence.ApiKey.create(
        id = "0cebc943-a0b9-4aa5-927d-65fa374bf0ec",
        userId = "023bfa40-e897-4dad-96db-9fd3cf001e79",
        apiKey = "5dac067a4c91de87ee04db3e3c34034e84eb4a599165bcc9741bb9a91e8212cb",
        secretKey = "dc9765e63b2b469a7bfb611fad8a10f2394d2b98b7a7105078356ec2a74164ea",
        permission = 3,
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      persistence.Password.create(
        id = "3401fdbb-428c-4cd5-961b-4ab9f171f18b",
        userId = "023bfa40-e897-4dad-96db-9fd3cf001e79",
        hash = "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8",
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      persistence.MailAddress.create(
        id = "17c91656-d5ba-404d-a2c8-7a2d94edcd24",
        userId = "023bfa40-e897-4dad-96db-9fd3cf001e79",
        address = "dummy1@example.jp",
        status = 1,
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      persistence.Group.create(
        id = "4d3c781e-6591-4f1f-a30c-ec1d2a991644",
        name = "dummy1",
        description = "",
        groupType = 1,
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      persistence.Member.create(
        id = "fb2e43d2-d415-49ee-affe-c9f542cb6d94",
        groupId = "4d3c781e-6591-4f1f-a30c-ec1d2a991644",
        userId = "023bfa40-e897-4dad-96db-9fd3cf001e79",
        role = 2,
        status = 1,
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )

      // user2 create
      persistence.User.create(
        id = "cc130a5e-cb93-4ec2-80f6-78fa83f9bd04",
        name = "dummy2",
        fullname = "テストダミーユーザー2",
        organization = "organization 2",
        title = "title 2",
        description = "description 2",
        imageId = defaultUserIconId,
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      persistence.Password.create(
        id = "a2158917-57b4-469c-a6dd-ded5fc826e71",
        userId = "cc130a5e-cb93-4ec2-80f6-78fa83f9bd04",
        hash = "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8",
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      persistence.MailAddress.create(
        id = "2d5d8b1a-66c0-4c6c-ba6e-ba40d3c0991e",
        userId = "cc130a5e-cb93-4ec2-80f6-78fa83f9bd04",
        address = "dummy2@example.jp",
        status = 1,
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      persistence.Group.create(
        id = "73855cc3-753a-4893-acc7-1f1c4453fb1b",
        name = "dummy2",
        description = "",
        groupType = 1,
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      persistence.Member.create(
        id = "d6e23730-a2bf-4415-88aa-7415d01ff9d6",
        groupId = "73855cc3-753a-4893-acc7-1f1c4453fb1b",
        userId = "cc130a5e-cb93-4ec2-80f6-78fa83f9bd04",
        role = 2,
        status = 1,
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )

      // user3 create
      persistence.User.create(
        id = "4aaefd45-2fe5-4ce0-b156-3141613f69a6",
        name = "dummy3",
        fullname = "テストダミーユーザー3",
        organization = "organization 3",
        title = "title 3",
        description = "description 3",
        imageId = defaultUserIconId,
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      persistence.Password.create(
        id = "f3af3ccd-e297-42ac-be11-636d963e2e8c",
        userId = "4aaefd45-2fe5-4ce0-b156-3141613f69a6",
        hash = "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8",
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      persistence.MailAddress.create(
        id = "05664194-ed49-4d18-9498-dc11fb55bab2",
        userId = "4aaefd45-2fe5-4ce0-b156-3141613f69a6",
        address = "dummy3@example.jp",
        status = 1,
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      persistence.Group.create(
        id = "cb898991-9f49-4574-8659-a8379390dc5d",
        name = "dummy3",
        description = "",
        groupType = 1,
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      persistence.Member.create(
        id = "33465c3e-b19d-41ea-9b82-ad6278f07b99",
        groupId = "cb898991-9f49-4574-8659-a8379390dc5d",
        userId = "4aaefd45-2fe5-4ce0-b156-3141613f69a6",
        role = 2,
        status = 1,
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )

      // user4 create
      persistence.User.create(
        id = "eb7a596d-e50c-483f-bbc7-50019eea64d7",
        name = "dummy4",
        fullname = "テストダミーユーザー4",
        organization = "organization 4",
        title = "title 4",
        description = "description 4",
        imageId = defaultUserIconId,
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      persistence.Password.create(
        id = "372ffef5-783e-4b9d-b2bb-474d7cc648a5",
        userId = "eb7a596d-e50c-483f-bbc7-50019eea64d7",
        hash = "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8",
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      persistence.MailAddress.create(
        id = "197e539a-c799-4d8b-95e9-fad62aac9c05",
        userId = "eb7a596d-e50c-483f-bbc7-50019eea64d7",
        address = "dummy4@example.jp",
        status = 1,
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      persistence.Group.create(
        id = "aed619f9-92f9-4298-a079-707594b72341",
        name = "dummy4",
        description = "",
        groupType = 1,
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      persistence.Member.create(
        id = "2401c492-4f0e-4e98-a967-49305832e06c",
        groupId = "aed619f9-92f9-4298-a079-707594b72341",
        userId = "eb7a596d-e50c-483f-bbc7-50019eea64d7",
        role = 2,
        status = 1,
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
    }
  }

  def deleteAllCreateData() {
    DB localTx { implicit s =>
      //  テーブルにinsertしたデータ削除(licenses, images以外)
      deleteAllData(deleteFrom(persistence.Annotation))
      deleteAllData(deleteFrom(persistence.DatasetAnnotation))
      deleteAllData(deleteFrom(persistence.DatasetImage))
      deleteAllData(deleteFrom(dsmoq.persistence.Dataset))
      deleteAllData(deleteFrom(persistence.FileHistory))
      deleteAllData(deleteFrom(persistence.File))
      deleteAllData(deleteFrom(persistence.GoogleUser))
      deleteAllData(deleteFrom(persistence.GroupImage))
      deleteAllData(deleteFrom(persistence.Group))
      deleteAllData(deleteFrom(persistence.MailAddress))
      deleteAllData(deleteFrom(persistence.Member))
      deleteAllData(deleteFrom(persistence.Ownership))
      deleteAllData(deleteFrom(persistence.Password))
      deleteAllData(deleteFrom(persistence.User))
      deleteAllData(deleteFrom(persistence.Task))
      deleteAllData(deleteFrom(persistence.TaskLog))
      deleteAllData(deleteFrom(persistence.ApiKey))

      // imagesテーブルのみpreset(システムデータ)以外を削除
      withSQL {
        val i = persistence.Image.syntax("i")
        deleteFrom(persistence.Image as i)
          .where
          .notInUuid(i.id, presetImageIds)
      }.update().apply

      // ファイル/画像の削除
      val fileDirs = new java.io.File(AppConf.fileDir).listFiles()
      fileDirs.foreach { x =>
        if (x.isDirectory) {
          x.listFiles.foreach { y =>
            deleteFile(y.getPath)
          }
          x.delete()
        }
      }
      val imageDirs = new java.io.File(AppConf.imageDir + "/upload").listFiles()
      imageDirs.foreach { x =>
        if (x.isDirectory) {
          x.listFiles.foreach { y =>
            deleteFile(y.getPath)
          }
          x.delete()
        }
      }
      deleteAllFile()

      val ts = DateTime.now
      persistence.User.create(
        id = "023bfa40-e897-4dad-96db-9fd3cf001e80",
        name = "dummy",
        fullname = "テストダミーユーザー",
        organization = "organization",
        title = "title",
        description = "description",
        imageId = defaultUserIconId,
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      persistence.Password.create(
        id = "3401fdbb-428c-4cd5-961b-4ab9f171f18c",
        userId = "023bfa40-e897-4dad-96db-9fd3cf001e80",
        hash = "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8",
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      persistence.MailAddress.create(
        id = "17c91656-d5ba-404d-a2c8-7a2d94edcd25",
        userId = "023bfa40-e897-4dad-96db-9fd3cf001e80",
        address = "dummy@example.jp",
        status = 1,
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      persistence.Group.create(
        id = "4d3c781e-6591-4f1f-a30c-ec1d2a991645",
        name = "dummy1",
        description = "",
        groupType = 1,
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      persistence.Member.create(
        id = "fb2e43d2-d415-49ee-affe-c9f542cb6d95",
        groupId = "4d3c781e-6591-4f1f-a30c-ec1d2a991645",
        userId = "023bfa40-e897-4dad-96db-9fd3cf001e80",
        role = 2,
        status = 1,
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      persistence.ApiKey.create(
        id = "0cebc943-a0b9-4aa5-927d-65fa374bf0eb",
        userId = "023bfa40-e897-4dad-96db-9fd3cf001e80",
        apiKey = "5dac067a4c91de87ee04db3e3c34034e84eb4a599165bcc9741bb9a91e8212ca",
        secretKey = "dc9765e63b2b469a7bfb611fad8a10f2394d2b98b7a7105078356ec2a74164ea",
        permission = 3,
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      persistence.User.create(
        id = "023bfa40-e897-4dad-96db-9fd3cf001e81",
        name = "test",
        fullname = "テストダミーユーザー",
        organization = "organization",
        title = "title",
        description = "description",
        imageId = defaultUserIconId,
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      persistence.Password.create(
        id = "3401fdbb-428c-4cd5-961b-4ab9f171f18d",
        userId = "023bfa40-e897-4dad-96db-9fd3cf001e81",
        hash = "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8",
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      persistence.MailAddress.create(
        id = "17c91656-d5ba-404d-a2c8-7a2d94edcd26",
        userId = "023bfa40-e897-4dad-96db-9fd3cf001e81",
        address = "test@example.jp",
        status = 1,
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      persistence.Group.create(
        id = "4d3c781e-6591-4f1f-a30c-ec1d2a991646",
        name = "test",
        description = "",
        groupType = 1,
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
      persistence.Member.create(
        id = "fb2e43d2-d415-49ee-affe-c9f542cb6d96",
        groupId = "4d3c781e-6591-4f1f-a30c-ec1d2a991646",
        userId = "023bfa40-e897-4dad-96db-9fd3cf001e81",
        role = 2,
        status = 1,
        createdBy = AppConf.systemUserId,
        createdAt = ts,
        updatedBy = AppConf.systemUserId,
        updatedAt = ts
      )
    }
  }

  private def deleteAllData(query: SQLBuilder[UpdateOperation])(implicit s: DBSession) {
    withSQL {
      query
    }.update().apply
  }

  private def deleteFile(path: String) {
    val file = new File(path)
    if (file.isDirectory) {
      file.listFiles.foreach { f =>
        deleteFile(f.getPath)
      }
    }
    file.delete()
  }

  private def deleteAllFile(): Unit =
  {
    val cre = new BasicAWSCredentials(AppConf.s3AccessKey, AppConf.s3SecretKey)
    val client = new AmazonS3Client(cre)
    val l = client.listObjects(AppConf.s3UploadRoot)

    l.getObjectSummaries.toList.foreach { obj =>
      client.deleteObject(AppConf.s3UploadRoot, obj.getKey)
    }
    l.getCommonPrefixes.toList.foreach { obj =>
      client.deleteObject(AppConf.s3UploadRoot, obj)
    }
  }
}
