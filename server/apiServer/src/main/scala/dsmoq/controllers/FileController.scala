package dsmoq.controllers

import com.typesafe.scalalogging.LazyLogging
import dsmoq.exceptions.{AccessDeniedException, NotAuthorizedException, NotFoundException}
import dsmoq.services.DatasetService.{DownloadFileLocalNormal, DownloadFileLocalZipped, DownloadFileS3Normal, DownloadFileS3Zipped}
import dsmoq.services.{DatasetService, User}
import org.apache.commons.io.input.BoundedInputStream
import org.scalatra.ScalatraServlet
import org.slf4j.MarkerFactory

import java.util.ResourceBundle
import javax.servlet.http.HttpServletRequest

import scala.util.{Failure, Success, Try}

class FileController(val resource: ResourceBundle) extends ScalatraServlet with LazyLogging with AuthTrait {

  /**
   * DatasetServiceのインスタンス
   */
  val datasetService = new DatasetService(resource)

  /**
    * ログマーカー
    */
  val LOG_MARKER = MarkerFactory.getMarker("FILE_LOG")

  /**
    * Rangeヘッダから開始、終了位置を取得するための正規表現
    */
  val RANGE_REGEX = "bytes=(\\d+)-(\\d*)".r

  /**
    * HEADリクエスト
    */
  head("/:datasetId/:id") {
    val datasetId = params("datasetId")
    val id = params("id")

    logger.info(LOG_MARKER, "Receive head request, datasetId={}, id={}", datasetId, id)

    // HEADリクエストではボディ要素として、バイナリは返さない。
    // このため、ストリームが設定されていない形でDownloadFileを取得する。
    val result = for {
      user <- getUser(allowGuest = true)
      fileInfo <- datasetService.getDownloadFileWithoutStream(datasetId, id, user)
    } yield {
      fileInfo
    }

    val rangeHeader = request.getHeader("Range")
    result match {
      case Success(DatasetService.DownloadFileLocalNormal(_, fileName, fileSize)) => {
        // ローカルファイルで、Zip内のファイル指定でない場合
        logger.debug(LOG_MARKER, "Found local file, fileName={}, fileSize={}", fileName, fileSize.toString)

        verifyRangeHeader(rangeHeader, fileSize) match {
          case VerifyRangeLegalFromTo(from, to) => {
            // from,toが指定されている場合
            logger.debug(LOG_MARKER, "Found Range header, Range={}", rangeHeader)

            progressTotalRequest(fileSize)
            // 空ボディを返す
            ""
          }
          case VerifyRangeLegalFrom(from) => {
            // fromのみが指定されている場合
            logger.debug(LOG_MARKER, "Found Range header, Range={}", rangeHeader)

            progressTotalRequest(fileSize)
            // 空ボディを返す
            ""
          }
          case VerifyRangeNotFound() => {
            // Rangeヘッダがない場合
            logger.debug(LOG_MARKER, "Not found Range header.")

            progressTotalRequest(fileSize)
            // 空ボディを返す
            ""
          }
          case _: VerifyRangeType => {
            logger.debug(LOG_MARKER, "Found Range header, but unsupported or illegal Range format. Range={}", rangeHeader)

            haltRangeNotSatisfiable(fileSize)
          }
        }
      }
      case Success(DatasetService.DownloadFileLocalZipped(_, fileName, fileSize)) => {
        // ローカルファイルで、Zip内のファイル指定の場合
        logger.debug(LOG_MARKER, "Found local zipped inner file, fileName={}, fileSize={}", fileName, fileSize.toString)

        verifyRangeHeader(rangeHeader, fileSize) match {
          case VerifyRangeNotFound() => {
            // Rangeヘッダがない場合
            logger.debug(LOG_MARKER, "Not found Range header.")

            progressTotalRequest(fileSize)
            // 空ボディを返す
            ""
          }
          case _: VerifyRangeType => {
            // Rangeヘッダがある場合
            logger.debug(LOG_MARKER, "Found Range header, but unsupported or illegal Range format. Range={}", rangeHeader)

            haltBadRequest()
          }
        }
      }
      case Success(DatasetService.DownloadFileS3Normal(redirectUrl)) => {
        // S3上のファイルで、Zip内のファイル指定でない場合
        logger.debug(LOG_MARKER, "Found S3 file, redirectUrl={}", redirectUrl)

        // リダイレクトヘッダを返す
        redirect(redirectUrl)
      }
      case Success(DatasetService.DownloadFileS3Zipped(_, fileName, fileSize)) => {
        // S3上のファイルで、Zip内のファイル指定の場合
        logger.debug(LOG_MARKER, "Found S3 zipped inner file, fileName={}, fileSize={}", fileName, fileSize.toString)

        verifyRangeHeader(rangeHeader, fileSize) match {
          case VerifyRangeNotFound() => {
            // Rangeヘッダがない場合
            logger.debug(LOG_MARKER, "Not found Range header.")

            progressTotalRequest(fileSize)
            // 空ボディを返す
            ""
          }
          case _: VerifyRangeType => {
            // Rangeヘッダがある場合
            logger.debug(LOG_MARKER, "Found Range header, but unsupported or illegal Range format. Range={}", rangeHeader)

            haltBadRequest()
          }
        }
      }
      case Failure(e) => {
        logger.error(LOG_MARKER, "Failure occurred.", e)

        e match {
          case _: NotFoundException => halt(status = 404, reason = "Not Found", body = "Not Found") // 404 Not Found
          case _: NotAuthorizedException => halt(status = 403, reason = "Forbidden", body = "Unauthorized") // 403 Forbidden (401 Unauthorized はブラウザ標準の認証処理が走るので不可)
          case _: AccessDeniedException => halt(status = 403, reason = "Forbidden", body = "Access Denied") // 403 Forbidden
          case _: Exception => halt(status = 500, reason = "Internal Server Error", body = "Internal Server Error") // 500 Internal Server Error
        }
      }
    }
  }

  /**
    * GETリクエスト
    */
  get("/:datasetId/:id") {
    val datasetId = params("datasetId")
    val id = params("id")

    logger.info(LOG_MARKER, "Receive get request, datasetId={}, id={}", datasetId, id)

    val result = for {
      user <- getUser(allowGuest = true)
      fileInfo <- datasetService.getDownloadFileWithStream(datasetId, id, user)
    } yield {
      fileInfo
    }

    val rangeHeader = request.getHeader("Range")
    result match {

      case Success(DatasetService.DownloadFileLocalNormal(data, fileName, fileSize)) => {
        // ローカルファイルで、Zip内のファイル指定でない場合
        logger.debug(LOG_MARKER, "Found local file, fileName={}, fileSize={}", fileName, fileSize.toString)

        verifyRangeHeader(rangeHeader, fileSize) match {
          case VerifyRangeLegalFromTo(from, to) => {
            // from,toが指定されている場合
            logger.debug(LOG_MARKER, "Found Range header, Range={}", rangeHeader)

            // 開始バイト数分だけ、skipする
            data.skip(from)

            val size = to - from
            val retData = new BoundedInputStream(data, size)

            val contentLength = size

            val contentRange = "bytes " + from.toString + "-" + (from + size).toString + "/" + fileSize.toString

            // ヘッダ要素の設定
            // 206 Partial Content
            progressPartialRequest(fileName, contentRange, contentLength)

            // Streamを返す
            retData
          }
          case VerifyRangeLegalFrom(from) => {
            // fromのみが指定されている場合
            logger.debug(LOG_MARKER, "Found Range header, Range={}", rangeHeader)

            val contentRange = "bytes " + from.toString + "-/" + fileSize.toString

            // ヘッダ要素の設定
            // 206 Partial Content
            progressPartialRequest(fileName, contentRange, fileSize - from)

            // 開始バイト数分だけ、skipする
            // skip後、残りのバイトを返すために、Streamを返す
            data.skip(from)
            data
          }
          case VerifyRangeNotFound() => {
            // Rangeヘッダがない場合
            logger.debug(LOG_MARKER, "Not found Range header.")

            // ヘッダ要素の設定
            // 200 OK
            progressTotalRequest(fileName, fileSize)

            data
          }
          case _: VerifyRangeType => {
            logger.debug(LOG_MARKER, "Found Range header, but unsupported or illegal Range format. Range={}", rangeHeader)

            haltRangeNotSatisfiable(fileSize)
          }
        }
      }
      case Success(DatasetService.DownloadFileS3Normal(redirectUrl)) => {
        // S3上のファイルで、Zip内のファイル指定でない場合
        logger.debug(LOG_MARKER, "Found S3 file, redirectUrl={}", redirectUrl)

        redirect(redirectUrl)
      }
      case Success(DatasetService.DownloadFileLocalZipped(fileData, fileName, fileSize)) => {
        // ローカルファイルで、Zip内のファイル指定の場合
        logger.debug(LOG_MARKER, "Found local zipped inner file, fileName={}, fileSize={}", fileName, fileSize.toString)

        verifyRangeHeader(rangeHeader, fileSize) match {
          case VerifyRangeNotFound() => {
            // Rangeヘッダがない場合
            logger.debug(LOG_MARKER, "Not found Range header.")

            // ヘッダ要素の設定
            // 200 OK
            progressTotalRequest(fileName, fileSize)

            fileData
          }
          case _: VerifyRangeType => {
            // Rangeヘッダがある場合
            logger.debug(LOG_MARKER, "Found Range header, but unsupported or illegal Range format. Range={}", rangeHeader)

            haltBadRequest()
          }
        }
      }
      case Success(DatasetService.DownloadFileS3Zipped(fileData, fileName, fileSize)) => {
        // S3上のファイルで、Zip内のファイル指定の場合
        logger.debug(LOG_MARKER, "Found S3 zipped inner file, fileName={}, fileSize={}", fileName, fileSize.toString)

        verifyRangeHeader(rangeHeader, fileSize) match {
          case VerifyRangeNotFound() => {
            // Rangeヘッダがない場合
            logger.debug(LOG_MARKER, "Not found Range header.")

            // ヘッダ要素の設定
            // 200 OK
            progressTotalRequest(fileName, fileSize)

            fileData
          }
          case _: VerifyRangeType => {
            // Rangeヘッダがある場合
            logger.debug(LOG_MARKER, "Found Range header, but unsupported or illegal Range format. Range={}", rangeHeader)

            haltBadRequest()
          }
        }
      }
      case Failure(e) => {
        logger.error(LOG_MARKER, "Failure occurred.", e)

        e match {
          case _: NotFoundException => halt(status = 404, reason = "Not Found", body = "Not Found") // 404 Not Found
          case _: NotAuthorizedException => halt(status = 403, reason = "Forbidden", body = "Unauthorized") // 403 Forbidden (401 Unauthorized はブラウザ標準の認証処理が走るので不可)
          case _: AccessDeniedException => halt(status = 403, reason = "Forbidden", body = "Access Denied") // 403 Forbidden
          case _: Exception => halt(status = 500, reason = "Internal Server Error", body = "Internal Server Error") // 500 Internal Server Error
        }
      }
    }
  }

  /**
    * Rangeヘッダが適切かどうか判定する。
    *
    * @param rangeHeader リクエストヘッダから取得したRangeヘッダの値
    * @param fileSize 対象となるファイルサイズ
    * @return 判定結果
    */
  private def verifyRangeHeader(rangeHeader: String, fileSize: Long): VerifyRangeType = {
    rangeHeader match {
      case RANGE_REGEX(fromPos, toPos) if (toPos != "") => {
        // Rangeヘッダがあり、from,toともに有意値の場合
        val from = fromPos.toLong
        val to = toPos.toLong

        if (to > fileSize || from > to || 0 > from) {
          VerifyRangeIllegalFromTo(from, to)
        } else {
          VerifyRangeLegalFromTo(from, to)
        }
      }
      case RANGE_REGEX(fromPos, toPos) if (toPos == "") => {
        // Rangeヘッダがあり、fromのみ有意値の場合
        val from = fromPos.toLong

        if (from > fileSize || 0 > from) {
          VerifyRangeIllegalFrom(from)
        } else {
          VerifyRangeLegalFrom(from)
        }
      }
      case _ if (rangeHeader != null) => {
        // Rangeヘッダがあり、フォーマットとして適さない場合
        VerifyRangeIllegalFormat()
      }
      case _ if (rangeHeader == null) => {
        // Rangeヘッダがない場合
        VerifyRangeNotFound()
      }
    }
  }

  /**
    * 指定の値をレスポンスヘッダに設定する。
    *
    * @param status ステータスコード
    * @param headers レスポンスヘッダに設定する項目を保持するMap
    */
  private def progress(
                        status: Int
                        , headers: Map[String, AnyRef] = Map.empty
                      ): Unit = {
    response.setStatus(status)

    if (headers.contains("Content-Disposition"))
      response.setHeader("Content-Disposition", headers.get("Content-Disposition").get.toString)

    if (headers.contains("Content-Type"))
      response.setContentType(headers.get("Content-Type").get.toString)

    if (headers.contains("Content-Length"))
      response.setContentLengthLong(headers.get("Content-Length").get.toString.toLong)

    if (headers.contains("Content-Range"))
      response.setHeader("Content-Range", headers.get("Content-Range").get.toString)
  }

  /**
    * レスポンスヘッダのContent-Dispositionに設定する文字列を返す。
    *
    * @param fileName ファイル名
    * @return Content-Dispositionに設定する文字列
    */
  private def genarateContentDispositionValue(fileName: String): String = {
    "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(fileName.split(Array[Char]('\\', '/')).last, "UTF-8")
  }

  /**
    * Content-Lengthのみをレスポンスヘッダに設定する。
    * ステータスコードは、200:OK とする。
    *
    * @param contentLength レスポンスで返すバイト長
    */
  private def progressTotalRequest(contentLength: Long): Unit = {
    val status = 200
    val headers = Map(
      "Content-Length" -> contentLength.toString)
    progress(status, headers)
  }

  /**
    * Rangeリクエストがない場合のレスポンスヘッダを設定する。
    * Content-Disposition, Content-Typeを設定する。
    * ステータスコードは、200:OK とする。
    *
    * @param fileName ファイル名
    * @param contentLength レスポンスで返すバイト長
    */
  private def progressTotalRequest(fileName: String, contentLength: Long): Unit = {
    // ボディ要素を送信する場合、Content-Lengthがヘッダにあると、ブラウザによっては
    // 正常にダウンロードできているにも関わらず、通信終了処理に問題があり、サーバ側
    // でStreamのクローズに関わる例外が発生する。このため、Content-Lengthは設定しない。
    // なお、上記問題はクライアントにcurlコマンドを使用した場合は発生しない。このため、
    // クライアント依存の問題である。

    val status = 200
    val headers = Map(
      "Content-Disposition" -> genarateContentDispositionValue(fileName)
      , "Content-Type" -> "application/octet-stream;charset=binary")
    progress(status, headers)
  }

  /**
    * Rangeリクエスト受付時のレスポンスヘッダを設定する。
    * Content-Disposition, Content-Type, Content-Lengthを設定する。
    * ステータスコードは、206:Partial Content とする。
    *
    * @param fileName ファイル名
    * @param contentRange レスポンスで返すContent-Range文字列
    * @param contentLength レスポンスで返すバイト長
    */
  private def progressPartialRequest(fileName: String, contentRange: String, contentLength: Long): Unit = {
    val status = 206
    val headers = Map(
      "Content-Disposition" -> genarateContentDispositionValue(fileName)
      , "Content-Type" -> "application/octet-stream;charset=binary"
      , "Content-Range" -> contentRange
      , "Content-Length" -> contentLength.toString)
    progress(status, headers)
  }

  /**
    * Rangeリクエストサポート外のファイル対象などでの場合のエラーレスポンスを返す。
    * 返すエラーコードは400:Bad Request とする。
    *
    * @param reason エラー原因。未指定の場合、デフォルト文字列を設定
    * @param body エラー本文。未指定の場合、デフォルト文字列を設定
    * @return the HTTP status reason to set, or null to leave unchanged.
    */
  private def haltBadRequest(
                        reason: String = "Unsupported range request for zip local file."
                        , body: String = "Unsupported range request for zip local file."
                        ): Nothing = {
    // 400 Bad Request
    halt(status = 400, reason = reason, body = body)
  }

  /**
    * 複数の範囲指定があるなどで、本サービスでサポートしない場合のエラーレスポンスを返す。
    * Content-Rangeを設定する。
    * 返すエラーコードは416:Range Not Satisfiable とする。
    *
    * @param fileSize ファイルサイズ
    * @param reason エラー原因。未指定の場合、デフォルト文字列を設定
    * @param body エラー本文。未指定の場合、デフォルト文字列を設定
    * @return the HTTP status reason to set, or null to leave unchanged.
    */
  private def haltRangeNotSatisfiable(
                        fileSize: Long
                        , reason: String = "Unsupported or illegal Range format."
                        , body: String = "Unsupported or illegal Range format."
                        ): Nothing = {
    // 複数の範囲指定があるなどで正規表現にマッチしない場合など、
    // 416エラーで返す場合
    val contentRange = "bytes */" + fileSize.toString

    // 416 Range Not Satisfiable
    halt(status = 416, headers = Map("Content-Range" -> contentRange), reason = reason, body = body)
  }

  /**
    * Rangeヘッダが有効か判定するためのケースオブジェクト
    */
  sealed trait VerifyRangeType

  /**
    * Rangeヘッダ：Rangeヘッダがない場合
    */
  case class VerifyRangeNotFound() extends VerifyRangeType

  /**
    * Rangeヘッダ：from, toがあり、有効なフォーマットで、範囲指定が有効な場合
    *
    * @param from 開始位置
    * @param to 終了位置
    */
  case class VerifyRangeLegalFromTo(from: Long, to: Long) extends VerifyRangeType

  /**
    * Rangeヘッダ：fromがあり、有効なフォーマットで、範囲指定が有効な場合
    *
    * @param from 開始位置
    */
  case class VerifyRangeLegalFrom(from: Long) extends VerifyRangeType

  /**
    * Rangeヘッダ：from, toがあり、有効なフォーマットだが、範囲指定が無効な場合
    *
    * @param from 開始位置
    * @param to 終了位置
    */
  case class VerifyRangeIllegalFromTo(from: Long, to: Long) extends VerifyRangeType

  /**
    * Rangeヘッダ：fromがあり、有効なフォーマットだが、範囲指定が無効な場合
    *
    * @param from 開始位置
    */
  case class VerifyRangeIllegalFrom(from: Long) extends VerifyRangeType

  /**
    * Rangeヘッダ：無効なフォーマットの場合
    */
  case class VerifyRangeIllegalFormat() extends VerifyRangeType
}
