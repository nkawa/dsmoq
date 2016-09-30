package dsmoq.controllers

import java.util.ResourceBundle

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.scalatra.ScalatraServlet
import org.slf4j.MarkerFactory

import com.typesafe.scalalogging.LazyLogging

import dsmoq.controllers.ResponseUtil.toActionResult
import dsmoq.exceptions.AccessDeniedException
import dsmoq.exceptions.NotFoundException
import dsmoq.services.ImageService

/**
 * /imagesにマッピングされるサーブレットクラス。
 * 画像の参照機能を提供する。
 *
 * @param resource リソースバンドル
 */
class ImageController(val resource: ResourceBundle) extends ScalatraServlet with LazyLogging with AuthTrait {

  /**
   * ImageServiceのインスタンス
   */
  val imageService = new ImageService(resource)

  /**
   * ログマーカー
   */
  val LOG_MARKER = MarkerFactory.getMarker("IMAGE_LOG")

  // いずれにもマッチしないGETリクエスト
  before("/*") {
    // TODO session control
  }

  // ユーザ画像取得
  get("/user/:userId/:imageId") {
    val imageId = params("imageId")
    val userId = params("userId")
    logger.info(LOG_MARKER, "Receive user image request, userId={}, imageId={}", userId, imageId)
    val ret = for {
      (image, name) <- imageService.getUserFile(userId, imageId, None)
    } yield {
      getImage(image, name)
    }
    toActionResult(ret)
  }

  // ユーザ画像取得(サイズ指定あり)
  get("/user/:userId/:imageId/:size") {
    val imageId = params("imageId")
    val userId = params("userId")
    val size = params.get("size")
    logger.info(LOG_MARKER, "Receive user image request, userId={}, imageId={}, size={}", userId, imageId, size)
    val ret = for {
      (image, name) <- imageService.getUserFile(userId, imageId, size)
    } yield {
      getImage(image, name)
    }
    toActionResult(ret)
  }

  // データセット画像取得
  get("/datasets/:datasetId/:imageId") {
    val imageId = params("imageId")
    val datasetId = params("datasetId")
    logger.info(LOG_MARKER, "Receive dataset image request, datasetId={}, imageId={}", datasetId, imageId)
    val ret = for {
      user <- getUser(allowGuest = true)
      (image, name) <- imageService.getDatasetFile(datasetId, imageId, None, user)
    } yield {
      getImage(image, name)
    }
    toActionResult(ret)
  }

  // データセット画像取得(サイズ指定あり)
  get("/datasets/:datasetId/:imageId/:size") {
    val imageId = params("imageId")
    val datasetId = params("datasetId")
    val size = params.get("size")
    logger.info(
      LOG_MARKER,
      "Receive dataset image request, datasetId={}, imageId={}, size={}", datasetId, imageId, size
    )
    val ret = for {
      user <- getUser(allowGuest = true)
      (image, name) <- imageService.getDatasetFile(datasetId, imageId, size, user)
    } yield {
      getImage(image, name)
    }
    toActionResult(ret)
  }

  // グループ画像取得
  get("/groups/:groupId/:imageId") {
    val imageId = params("imageId")
    val groupId = params("groupId")
    logger.info(LOG_MARKER, "Receive group image request, groupId={}, imageId={}", groupId, imageId)
    val ret = for {
      (image, name) <- imageService.getGroupFile(groupId, imageId, None)
    } yield {
      getImage(image, name)
    }
    toActionResult(ret)
  }

  // グループ画像取得(サイズ指定あり)
  get("/groups/:groupId/:imageId/:size") {
    val imageId = params("imageId")
    val groupId = params("groupId")
    val size = params.get("size")
    logger.info(
      LOG_MARKER,
      "Receive group image request, groupId={}, imageId={}, size={}", groupId, imageId, size
    )
    val ret = for {
      (image, name) <- imageService.getGroupFile(groupId, imageId, size)
    } yield {
      getImage(image, name)
    }
    toActionResult(ret)
  }

  /**
   * リクエストヘッダにContent-Disposition、Content-Typeを設定し、ファイルを返す。
   *
   * @param image 画像ファイル
   * @param name ファイル名
   * @return 画像ファイル
   */
  private def getImage(image: java.io.File, name: String) = {
    logger.debug(LOG_MARKER, "getImage succeeded")
    response.setHeader("Content-Disposition", "inline; filename=" + name)
    response.setHeader("Content-Type", "application/octet-stream;charset=binary")
    image
  }
}
