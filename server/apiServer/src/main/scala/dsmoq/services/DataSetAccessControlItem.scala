package dsmoq.services

/**
 * データセットのアクセス権を表すケースクラス
 *
 * @param id ユーザID/グループID
 * @param ownerType オーナー種別(@see dsmoq.persistence.OwnerType)
 * @param accessLevel アクセスレベル(@see dsmoq.persistence.UserAccessLevel、GroupAccessLevel)
 */
case class DataSetAccessControlItem(id: String, ownerType: Int, accessLevel: Int)
