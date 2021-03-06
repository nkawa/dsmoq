package dsmoq.maintenance.services

import java.util.UUID

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.typesafe.scalalogging.Logger
import org.slf4j.Marker

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
   * オプショナルな値から値を取得する。
   *
   * @tparam T オプショナルな値の型
   * @param target オプショナルな値
   * @param name 対象の名前(見つからなかった時のメッセージに含まれます)
   * @param 文字列
   *        Failure(ServiceException) オプショナルな値が未指定の場合
   */
  def require[T](target: Option[T], name: String): Try[T] = {
    target match {
      case Some(t) => Success(t)
      case None => Failure(new ServiceException(s"${name}の指定がありません。"))
    }
  }

  /**
   * オプショナルな値から値を取得する。
   *
   * @param target オプショナルな値
   * @param name 対象の名前(見つからなかった時のメッセージに含まれます)
   * @throws Failure(ServiceException) オプショナルな値が未指定の場合
   */
  def requireString(target: Option[String], name: String): Try[String] = {
    target match {
      case Some(t) if t.nonEmpty => Success(t)
      case _ => Failure(new ServiceException(s"${name}の指定がありません。"))
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

  /**
   * メソッド呼び出し時のログメッセージを作成する。
   *
   * @param serviceName サービス名
   * @param methodName メソッド名
   * @param params 引数のリスト
   * @return 構築したログメッセージ文字列
   */
  def formatLogMessage(
    serviceName: String,
    methodName: String,
    params: Any*
  ): String = {
    s"MethodCall:${serviceName},${methodName},[${params.map(_.toString).mkString(",")}]"
  }

  /**
   * チェック対象がFailure(ServiceException)の場合にロギングを行う。
   *
   * @param logger Logger
   * @param marker LogMarker
   * @param result チェック対象
   * @return チェック対象
   */
  def withErrorLogging[T](logger: Logger, marker: Marker, result: Try[T]): Try[T] = {
    result match {
      case Failure(e) => {
        e match {
          case e: ServiceException if e.withLogging => logger.error(marker, e.getMessage, e)
          case _ => // do nothing
        }
      }
      case _ => // do nothing
    }
    result
  }
}
