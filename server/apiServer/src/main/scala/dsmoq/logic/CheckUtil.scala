package dsmoq.logic

import scalikejdbc.DB

import scala.util.{Try, Success, Failure}

import dsmoq.exceptions.{InputCheckException, NotFoundException}
import dsmoq.persistence
import dsmoq.services.{AccountService, GroupService}

/**
 * 入力チェックのユーティリティクラス
 */
object CheckUtil {
  /**
   * 項目の値を取得する。取得できない場合は、エラーを返す。
   *
   * @param name 項目名
   * @param value 項目の値(optional)
   * @param isUrlParam URLパラメータか否か
   * @return 項目の値。エラーがあれば、Failureで例外を包んで返す。
   */
  def require[T](name: String, value: Option[T], isUrlParam: Boolean): Try[T] = {
    value match {
      case None => Failure(new InputCheckException(name, s"${name} が指定されていません", isUrlParam))
      case Some(x) => Success(x)
    }
  }
  /**
   * リストに要素があるかをチェックする。ない場合は、エラーを返す。
   *
   * @param name 項目名
   * @param value チェック対象のリスト 
   * @return エラーがあれば、Failureで例外を包んで返す。
   */
  def hasElement[T](name: String, value: Seq[T]): Try[Unit] = {
    if (value.isEmpty) {
      Failure(new InputCheckException(name, s"${name} の件数が0件です", false))
    }else {
      Success(Unit)
    }
  }
  /**
   * 対象が規定の範囲にあるかをチェックする。ない場合は、エラーを返す。
   *
   * @param name 項目名
   * @param value チェック対象 
   * @param validRange 規定の範囲
   * @return エラーがあれば、Failureで例外を包んで返す。
   */
  def range[T](name: String, value: T, validRange: Seq[T]): Try[Unit] = {
    if (validRange.contains(value)) {
      Success(Unit)
    } else {
      Failure(new InputCheckException(name, s"${name} (値：${value})は有効な値ではありません", false))
    }
  }
  /**
   * 対象が規定の範囲にあるかをチェックする。ない場合は、エラーを返す。
   *
   * @param name 項目名
   * @param value チェック対象(optional)
   * @param validRange 規定の範囲
   * @return エラーがあれば、Failureで例外を包んで返す。
   */
  def range(name: String, value: Option[String], validRange: Seq[String]): Try[Unit] = {
    value match {
      case None => Success(Unit)
      case Some(v) => range(name, v, validRange)
    }
  }
  /**
   * 規定の操作でチェックする。チェックに違反した場合は、エラーを返す。
   *
   * @param name 項目名
   * @param pred チェック処理
   * @param message エラーメッセージ
   * @return エラーがあれば、Failureで例外を包んで返す。
   */
  def check(name: String, pred: => Boolean, message: String): Try[Unit] = {
    if (pred) {
      Success(Unit)
    } else {
      Failure(new InputCheckException(name, message, false))
    }
  }
  /**
   * Seqの要素に対して、既定のチェックを行う。チェックに違反した場合は、最初に違反したエラーを返す。
   *
   * @param seq チェック対象のSeq
   * @param check チェック処理
   * @return エラーがあれば、Failureで例外を包んで返す。
   */
  def seqCheck[T](seq: Seq[T])(check: T => Try[Unit]): Try[Unit] = {
    val ret = seq.map { x => check(x) }
    for (r <- ret) {
      r match {
        case Success(_) => // do nothing
        case f@Failure(_) => return f
      }
    }
    Success(Unit)
  }
  /**
   * 項目の値が空文字列(ホワイトスペース、全角スペースのみからなる文字列も含む)である場合にエラーを返す。
   * 
   * @param name 項目名
   * @param value 項目の値(optional)
   * @param isUrlParam URLパラメータか否か
   * @return エラーがあれば、Failureで例外を包んで返す。
   */
  def nonEmpty(name: String, value: String, isUrlParam: Boolean): Try[Unit] = {
    if (StringUtil.trimAllSpaces(value).isEmpty) {
      Failure(new InputCheckException(name, s"${name} を入力してください", isUrlParam))
    } else {
      Success(Unit)
    }
  }
  /**
   * 項目の値がUUIDの形式として不正な場合にエラーを返す。
   * 
   * @param name 項目名
   * @param value 項目の値(optional)
   * @param isUrlParam URLパラメータか否か
   * @return エラーがあれば、Failureで例外を包んで返す。
   */
  def uuid(name: String, value: String, isUrlParam: Boolean): Try[Unit] = {
    if (StringUtil.isUUID(value)) {
      Success(Unit)
    } else {
      Failure(new InputCheckException(name, s"${name}(値：${value}) はUUIDの形式ではありません", isUrlParam))
    }
  }
  def uuid(name: String, value: Option[String], isUrlParam: Boolean): Try[Unit] = {
    value match {
      case None => Success(Unit)
      case Some(v) => uuid(name, v, isUrlParam)
    }
  }
  /**
   * 対象のユーザ名が既に存在している場合にエラーを返す。
   *
   * @param targetName 項目名
   * @param userId ユーザID
   * @param name ユーザ名
   * @return エラーがあれば、Failureで例外を包んで返す。
   */
  def existsSameName(targetName: String, userId: String, name: String): Try[Unit] = {
    DB.readOnly { implicit s =>
      if (AccountService.existsSameName(userId, name)) {
        Failure(new InputCheckException(targetName, s"ユーザ名：${name} はすでに存在しています", false))
      } else {
        Success(Unit)
      }
    }
  }
  /**
   * 対象のEmailアドレスが既に存在している場合にエラーを返す。
   *
   * @param name 項目名
   * @param userId ユーザID
   * @param email Emailアドレス
   * @return エラーがあれば、Failureで例外を包んで返す。
   */
  def existsSameEmail(name: String, userId: String, email: String): Try[Unit] = {
    DB.readOnly { implicit s =>
      if (AccountService.existsSameEmail(userId, email)) {
        Failure(new InputCheckException(name, s"Emailアドレス：${email} はすでに登録されています", false))
      } else {
        Success(Unit)
      }
    }
  }
  /**
   * 対象のユーザがGoogleアカウントユーザである場合にエラーを返す。
   *
   * @param name 項目名
   * @param userId ユーザID
   * @param message エラーメッセージ
   * @return エラーがあれば、Failureで例外を包んで返す。
   */
  def googleUser(name: String, userId: String, message: String): Try[Unit] = {
    DB.readOnly { implicit s =>
      if (AccountService.isGoogleUser(userId)) {
        Failure(new InputCheckException(name, message, false))
      } else {
        Success(Unit)
      }
    }
  }
  /**
   * 対象のユーザがGoogleアカウントユーザである場合に、名前を変更しようとした場合、エラーを返す。
   *
   * @param name ユーザ名
   * @param userId ユーザID
   * @return エラーがあれば、Failureで例外を包んで返す。
   */
  def googleUserChangeName(name: String, userId: String): Try[Unit] = {
    DB.readOnly { implicit s =>
      if (AccountService.isGoogleUser(userId)) {
        // Googleアカウントユーザーはアカウント名の変更禁止(importスクリプトでusersテーブルのname列を使用しているため)
        persistence.User.find(userId) match {
          case None => Failure(new NotFoundException)
          case Some(x) if x.name != name => Failure(new InputCheckException(name, s"Googleアカウントユーザのアカウント名は変更できません", false))
          case Some(_) => Success(Unit)
        }
      } else {
        Success(Unit)
      }
    }
  }
  /**
   * パスワードが正しいかどうかをチェックする。正しくない場合、エラーを返す。
   *
   * @param userId ユーザID
   * @param passowrd パスワード
   * @return パスワードオブジェクト。エラーがあれば、Failureで例外を包んで返す。
   */
  def passwordCheck(userId: String, password: String): Try[persistence.Password] = {
    DB.readOnly { implicit s =>
      AccountService.getCurrentPassword(userId, password) match {
        case None => Failure(new InputCheckException("d.currentPassword", s"パスワードが一致しません", false))
        case Some(p) => Success(p)
      }
    }
  }
  /**
   * ライセンスIDが正しいかどうかをチェックする。正しくない場合、エラーを返す。
   *
   * @param licenseId ライセンスID
   * @return エラーがあれば、Failureで例外を包んで返す。
   */
  def validLicense(licenseId: String): Try[Unit] = {
    DB.readOnly { implicit s =>
      persistence.License.find(licenseId) match {
        case None => Failure(new InputCheckException("d.license", s"${licenseId} は無効なライセンスIDです", false))
        case Some(_) => Success(Unit)
      }
    }
  }
  /**
   * 対象のグループ名が既に存在している場合にエラーを返す。
   *
   * @param name 項目名
   * @param groupName グループ名
   * @return エラーがあれば、Failureで例外を包んで返す。
   */
  def existsSameNameGroup(name: String, groupName: String): Try[Unit] = {
    DB.readOnly { implicit s =>
      if (GroupService.existsSameNameGroup(groupName)) {
        Failure(new InputCheckException(name, s"グループ名：${groupName} はすでに登録されています", false))
      } else {
        Success(Unit)
      }
    }
  }
}
