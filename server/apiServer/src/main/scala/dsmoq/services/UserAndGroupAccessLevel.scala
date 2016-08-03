package dsmoq.services

/**
 * ユーザおよびグループのAccessLevel定数定義体
 */
object UserAndGroupAccessLevel {

  /**
   * Denyを表すAccessLevelの値
   */
  val DENY = 0

  /**
   * AllowDownloadを表すAccessLevelの値
   */ 
  val ALLOW_DOWNLOAD = 2

  /**
   * オーナー、またはプロバイダを表すAccessLevelの値
   */
  val OWNER_OR_PROVIDER = 3
}
