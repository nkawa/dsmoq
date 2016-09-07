package dsmoq.maintenance.services

import java.util.UUID

import scala.util.Failure
import scala.util.Success
import scala.util.Try

/**
 * サービスクラスで利用するユーティリティクラス
 */
object Util {
  /**
   * 指定されたIDが全てUUID形式であることを確認する。
   *
   * @param ids 検査するID
   * @return 処理結果、idsにUUID形式でないものが含まれていればFailure(ServiceException)
   */
  def checkUuids(ids: Seq[String]): Try[Unit] = {
    ids.map(checkUuid).filter(_.isFailure).headOption.getOrElse(Success(()))
  }

  /**
   * 指定されたIDがUUID形式であることを確認する。
   *
   * @param id 検査するID
   * @return 処理結果、idがUUID形式でなければFailure(ServiceException)
   */
  def checkUuid(id: String): Try[Unit] = {
    if (isUUID(id)) {
      Success(())
    } else {
      Failure(new ServiceException("指定したIDの形式が不正です。"))
    }
  }

  /**
   * 指定された文字列がUUID形式かを返す。
   *
   * @param str 検査する文字列
   * @return strがUUID形式であればtrue、そうでなければfalse
   */
  def isUUID(str: String): Boolean = {
    try {
      val uuid = UUID.fromString(str.trim)
      uuid.toString.toLowerCase == str.trim.toLowerCase
    } catch {
      case _: IllegalArgumentException => false
    }
  }
}
