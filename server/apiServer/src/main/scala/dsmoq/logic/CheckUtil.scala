package dsmoq.logic

import java.util.ResourceBundle

import scalikejdbc.DB

import com.typesafe.scalalogging.LazyLogging
import org.slf4j.MarkerFactory
import org.scalatra.servlet.FileItem

import scala.util.{Try, Success, Failure}

import dsmoq.ResourceNames
import dsmoq.exceptions.{InputCheckException, NotFoundException}
import dsmoq.persistence
import dsmoq.services.{AccountService, GroupService}

/**
 * 入力チェックのユーティリティクラス
 */
class CheckUtil(resource: ResourceBundle) extends LazyLogging {

  /**
    * ログマーカー
    */
  private val LOG_MARKER = MarkerFactory.getMarker("INPUT_CHECK_LOG")

  /**
   * URLパラメータ向けに項目の値を取得する。取得できない場合は、エラーを返す。
   *
   * @param name 項目名
   * @param value 項目の値(optional)
   * @return 項目の値。エラーがあれば、Failureで例外を包んで返す。
   *         返却する可能性のある例外は、InputCheckExceptionである。
   */
  def requireForUrl[T](name: String, value: Option[T]): Try[T] = require(name, value, true)

  /**
   * FORMパラメータ向けに項目の値を取得する。取得できない場合は、エラーを返す。
   *
   * @param name 項目名
   * @param value 項目の値(optional)
   * @return 項目の値。エラーがあれば、Failureで例外を包んで返す。
   *         返却する可能性のある例外は、InputCheckExceptionである。
   */
  def requireForForm[T](name: String, value: Option[T]): Try[T] = require(name, value, false)

  /**
   * 項目の値を取得する。取得できない場合は、エラーを返す。
   *
   *         返却する可能性のある例外は、InputCheckExceptionである。
   * @param name 項目名
   * @param value 項目の値(optional)
   * @param isUrlParam URLパラメータか否か
   * @return 項目の値。エラーがあれば、Failureで例外を包んで返す。
   *         返却する可能性のある例外は、InputCheckExceptionである。
   */
  private def require[T](name: String, value: Option[T], isUrlParam: Boolean): Try[T] = {
    value match {
      case None => 
        logger.error(LOG_MARKER, "require check failed: name:{}, value:none", name)
        Failure(new InputCheckException(name, resource.getString(ResourceNames.REQUIRE_TARGET).format(name), isUrlParam))
      case Some(x) => Success(x)
    }
  }

  /**
   * リストに要素があるかをチェックする。ない場合は、エラーを返す。
   *
   * @param name 項目名
   * @param value チェック対象のリスト 
   * @return エラーがあれば、Failureで例外を包んで返す。
   *         返却する可能性のある例外は、InputCheckExceptionである。
   */
  def hasElement[T](name: String, value: Seq[T]): Try[Unit] = {
    if (value.isEmpty) {
      logger.error(LOG_MARKER, "hasElement check failed: name:{}, value:{}", name, value)
      Failure(new InputCheckException(name, resource.getString(ResourceNames.EMPTY).format(name), false))
    }else {
      Success(Unit)
    }
  }

  /**
   * 対象が列挙された要素内にあるかをチェックする。ない場合は、エラーを返す。
   *
   * @param name 項目名
   * @param value チェック対象 
   * @param elements 列挙された要素
   * @return エラーがあれば、Failureで例外を包んで返す。
   *         返却する可能性のある例外は、InputCheckExceptionである。
   */
  def contains[T](name: String, value: T, elements: Seq[T]): Try[Unit] = {
    if (elements.contains(value)) {
      Success(Unit)
    } else {
      logger.error(LOG_MARKER, "contains check failed: name:{}, value:{}", name, value.toString)
      Failure(new InputCheckException(name, resource.getString(ResourceNames.NOT_CONTAINS_RANGE).format(name, value.toString), false))
    }
  }

  /**
   * 対象が列挙された要素内にあるかをチェックする。ない場合は、エラーを返す。
   *
   * @param name 項目名
   * @param value チェック対象(optional)
   * @param elements 列挙された要素
   * @return エラーがあれば、Failureで例外を包んで返す。
   *         返却する可能性のある例外は、InputCheckExceptionである。
   */
  def contains(name: String, value: Option[String], elements: Seq[String]): Try[Unit] = {
    value match {
      case None => Success(Unit)
      case Some(v) => contains(name, v, elements)
    }
  }

  /**
   * 呼び出し元指定の操作でチェックを行う。チェックに違反した場合は、エラーを返す。
   *
   * @param name 項目名
   * @param invoker 呼び出し元指定のチェック処理
   * @param message エラーメッセージ
   * @return エラーがあれば、Failureで例外を包んで返す。
   *         返却する可能性のある例外は、InputCheckExceptionである。
   */
  def invoke(name: String, invoker: => Boolean, message: String): Try[Unit] = {
    if (invoker) {
      Success(Unit)
    } else {
      logger.error(LOG_MARKER, "invoke check failed: name:{}, message:{}", name, message)
      Failure(new InputCheckException(name, message, false))
    }
  }

  /**
   * Seqの要素に対して、呼び出し元指定の操作でチェックを行う。チェックに違反した場合は、最初に違反したエラーを返す。
   *
   * @param seq チェック対象のSeq
   * @param invoker 呼び出し元指定のチェック処理
   * @return エラーがあれば、Failureで例外を包んで返す。
   *         返却する可能性のある例外は、InputCheckExceptionである。
   */
  def invokeSeq[T](seq: Seq[T])(invoker: T => Try[Unit]): Try[Unit] = {
    val ret = seq.map { x => invoker(x) }
    for (r <- ret) {
      r match {
        case Success(_) => // do nothing
        case f@Failure(_) =>
          logger.error(LOG_MARKER, "invokeSeq check failed: seq:{}", seq)
          return f
      }
    }
    Success(Unit)
  }

  /**
   * URLパラメータ向けに、項目の値が空文字列(ホワイトスペース、全角スペースのみからなる文字列も含む)でないかをチェックする。チェックに違反した場合はエラーを返す。
   * 
   * @param name 項目名
   * @param value 項目の値
   * @return エラーがあれば、Failureで例外を包んで返す。
   *         返却する可能性のある例外は、InputCheckExceptionである。
   */
  def nonEmptyTrimmedSpacesForUrl(name: String, value: String): Try[Unit] = nonEmptyTrimmedSpaces(name, value, true)

  /**
   * FORMパラメータ向けに、項目の値が空文字列(ホワイトスペース、全角スペースのみからなる文字列も含む)でないかをチェックする。チェックに違反した場合はエラーを返す。
   * 
   * @param name 項目名
   * @param value 項目の値
   * @return エラーがあれば、Failureで例外を包んで返す。
   *         返却する可能性のある例外は、InputCheckExceptionである。
   */
  def nonEmptyTrimmedSpacesForForm(name: String, value: String): Try[Unit] = nonEmptyTrimmedSpaces(name, value, false)

  /**
   * 項目の値が空文字列(ホワイトスペース、全角スペースのみからなる文字列も含む)である場合にエラーを返す。
   * 
   * @param name 項目名
   * @param value 項目の値(optional)
   * @param isUrlParam URLパラメータか否か
   * @return エラーがあれば、Failureで例外を包んで返す。
   *         返却する可能性のある例外は、InputCheckExceptionである。
   */
  private def nonEmptyTrimmedSpaces(name: String, value: String, isUrlParam: Boolean): Try[Unit] = {
    if (StringUtil.trimAllSpaces(value).isEmpty) {
      logger.error(LOG_MARKER, "nonEmptyTrimmedSpace check failed: name:{}", name)
      Failure(new InputCheckException(name, resource.getString(ResourceNames.REQUIRE_NON_EMPTY).format(name), isUrlParam))
    } else {
      Success(Unit)
    }
  }

  /**
   * URLパラメータ向けに、項目の値がUUIDの形式として不正かどうかをチェックする。チェックに違反した場合はエラーを返す。
   * 
   * @param name 項目名
   * @param value 項目の値
   * @return エラーがあれば、Failureで例外を包んで返す。
   *         返却する可能性のある例外は、InputCheckExceptionである。
   */
  def validUuidForUrl(name: String, value: String): Try[Unit] = validUuid(name, value, true)

  /**
   * FORMパラメータ向けに、項目の値がUUIDの形式として不正かどうかをチェックする。チェックに違反した場合はエラーを返す。
   * 
   * @param name 項目名
   * @param value 項目の値
   * @return エラーがあれば、Failureで例外を包んで返す。
   *         返却する可能性のある例外は、InputCheckExceptionである。
   */
  def validUuidForForm(name: String, value: String): Try[Unit] = validUuid(name, value, false)

  /**
   * 項目の値がUUIDの形式として不正な場合にエラーを返す。
   * 
   * @param name 項目名
   * @param value 項目の値
   * @param isUrlParam URLパラメータか否か
   * @return エラーがあれば、Failureで例外を包んで返す。
   *         返却する可能性のある例外は、InputCheckExceptionである。
   */
  def validUuid(name: String, value: String, isUrlParam: Boolean): Try[Unit] = {
    if (StringUtil.isUUID(value)) {
      Success(Unit)
    } else {
      logger.error(LOG_MARKER, "validUuid check failed: name:{}, value:{}", name, value)
      Failure(new InputCheckException(name, resource.getString(ResourceNames.INVALID_UUID).format(name, value), isUrlParam))
    }
  }

  /**
   * URLパラメータ向けに、Optionalな項目の値がUUIDの形式として不正かどうかをチェックする。チェックに違反した場合はエラーを返す。
   * 
   * @param name 項目名
   * @param value 項目の値(optional)
   * @return エラーがあれば、Failureで例外を包んで返す。
   *         返却する可能性のある例外は、InputCheckExceptionである。
   */
  def validUuidForUrl(name: String, value: Option[String]): Try[Unit] = validUuid(name, value, true)

  /**
   * FORMパラメータ向けに、Optionalな項目の値がUUIDの形式として不正かどうかをチェックする。チェックに違反した場合はエラーを返す。
   * 
   * @param name 項目名
   * @param value 項目の値(optional)
   * @return エラーがあれば、Failureで例外を包んで返す。
   *         返却する可能性のある例外は、InputCheckExceptionである。
   */
  def validUuidForForm(name: String, value: Option[String]): Try[Unit] = validUuid(name, value, false)

  /**
   * Optionalな項目の値がUUIDの形式として不正な場合にエラーを返す。
   * 
   * @param name 項目名
   * @param value 項目の値(optional)
   * @param isUrlParam URLパラメータか否か
   * @return エラーがあれば、Failureで例外を包んで返す。
   *         返却する可能性のある例外は、InputCheckExceptionである。
   */
  def validUuid(name: String, value: Option[String], isUrlParam: Boolean): Try[Unit] = {
    value match {
      case None => Success(Unit)
      case Some(v) => validUuid(name, v, isUrlParam)
    }
  }

  /**
   * ファイルが0byteであるかどうかをチェックする。チェックに違反した場合はエラーを返す。
   * 
   * @param name 項目名
   * @param file ファイル
   * @return エラーがあれば、Failureで例外を包んで返す。
   *         返却する可能性のある例外は、InputCheckExceptionである。
   */
  def checkNonZeroByteFile(name: String, file: FileItem): Try[Unit] = {
    if (file.getSize > 0) {
      Success(Unit)
    } else {
      Failure(new InputCheckException(name, resource.getString(ResourceNames.SELECT_EMPTY_FILE), false))
    }
  }

  /**
   * 数値が0以上であるかどうかをチェックする。チェックに違反した場合はエラーを返す。
   * 
   * @param name 項目名
   * @param num 数値(optional)
   * @return エラーがあれば、Failureで例外を包んで返す。
   *         返却する可能性のある例外は、InputCheckExceptionである。
   */
  def checkNonMinusNumber(name: String, num: Option[Int]): Try[Unit] = {
    num match {
      case Some(n) =>
        if (n >= 0) {
          Success(Unit)
        } else {
          Failure(new InputCheckException(name, resource.getString(ResourceNames.REQUIRE_NON_MINUS), false))
        }
      case None => Success(Unit)
    }
  }
}
