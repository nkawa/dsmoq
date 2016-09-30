package dsmoq.controllers.json

import org.json4s.CustomSerializer
import org.json4s.Extraction
import org.json4s.Formats
import org.json4s.JValue
import org.json4s.JObject
import org.json4s.JsonDSL._

import dsmoq.services.DataSetAttribute
import dsmoq.services.json.SearchDatasetCondition

sealed trait SearchDatasetParams

/**
 * GET /api/datasetsのリクエストに使用するJSON型を取りまとめるオブジェクト
 */
object SearchDatasetParams {

  /**
   * 検索条件(SearchCondition形式)のJSON型のケースクラス
   *
   * @param query 検索文字列
   * @param limit 検索件数上限
   * @param offset 検索位置
   */
  case class Condition(
    query: SearchDatasetCondition,
    limit: Option[Int] = None,
    offset: Option[Int] = None
  ) extends SearchDatasetParams

  /**
   * 検索条件(query/owners/groups/attirbutes形式)のJSON型のケースクラス
   *
   * @param query 検索文字列
   * @param owners 検索に使用するオーナーID
   * @param groups 検索に使用するグループ名
   * @param attributes 検索に使用する属性
   * @param limit 検索件数上限
   * @param offset 検索位置
   * @param orderby 検索のソート順を規定する文字列
   */
  case class Params(
    query: Option[String] = None,
    owners: List[String] = List.empty,
    groups: List[String] = List.empty,
    attributes: List[DataSetAttribute] = List.empty,
    limit: Option[Int] = None,
    offset: Option[Int] = None,
    orderby: Option[String] = None
  ) extends SearchDatasetParams

  /**
   * SearchDatasetParamsを構築する。
   *
   * @return SearchDatasetParams
   */
  def apply(): SearchDatasetParams = Condition(SearchDatasetCondition.Query())

  /**
   * JSONオブジェクトをSearchDatasetParamsに展開する。
   *
   * @param x JSONオブジェクト
   * @param formats JSONフォーマット
   * @return SearchDatasetParams
   */
  def unapply(x: JValue)(implicit formats: Formats): Option[SearchDatasetParams] = {
    x.extractOpt[Condition] orElse x.extractOpt[Params]
  }
}

/**
 * GET /api/datasetsのリクエストに使用するJSON型のシリアライザ・デシリアライザ
 */
object SearchDatasetParamsSerializer extends CustomSerializer[SearchDatasetParams](implicit formats => {
  /**
   * JSON型のデシリアライザ
   */
  val deserializer: PartialFunction[JValue, SearchDatasetParams] = {
    case SearchDatasetParams(p) => p
  }

  /**
   * JSON型のシリアライザ
   */
  val serializer: PartialFunction[Any, JValue] = {
    case x: SearchDatasetParams.Condition => {
      ("query" -> Extraction.decompose(x.query)) ~
        ("limit" -> x.limit) ~
        ("offset" -> x.offset)
    }
    case x: SearchDatasetParams.Params => {
      ("query" -> x.query) ~
        ("owners" -> Extraction.decompose(x.owners)) ~
        ("groups" -> Extraction.decompose(x.groups)) ~
        ("attributes" -> Extraction.decompose(x.attributes)) ~
        ("limit" -> x.limit) ~
        ("offset" -> x.offset) ~
        ("orderby" -> x.orderby)
    }
  }
  (deserializer, serializer)
})

/**
 * PUT /api/datasets/:datasetId/files/:fileId/metadataのリクエストに使用するJSON型のケースクラス
 *
 * @param name ファイル名
 * @param description 説明
 */
case class UpdateDatasetFileMetadataParams(
  name: Option[String] = None,
  description: Option[String] = None
)

/**
 * PUT /api/datasets/:datasetId/metadataのリクエストに使用するJSON型のケースクラス
 *
 * @param name データセット名
 * @param description 説明
 * @param license ライセンスID
 * @param attributes 属性
 */
case class UpdateDatasetMetaParams(
  name: Option[String] = None,
  description: Option[String] = None,
  license: Option[String] = None,
  attributes: List[DataSetAttribute] = List.empty
)

/**
 * データセットの画像変更のリクエストに使用するJSON型のケースクラス
 *
 * 具体的には、以下のAPIで使用する。
 * PUT /api/datasets/:datasetId/images/primary
 * PUT /api/datasets/:datasetId/images/featured
 *
 * @param imageId データセットに設定する画像ID
 */
case class ChangePrimaryImageParams(
  imageId: Option[String] = None
)

/**
 * PUT /api/datasets/:datasetId/guest_accessのリクエストに使用するJSON型のケースクラス
 *
 * @param accessLevel アクセスレベル(@see dsmoq.persistence.DefaultAccessLevel)
 */
case class UpdateDatasetGuestAccessParams(
  accessLevel: Option[Int] = None
)

/**
 * PUT /api/datasets/:datasetId/storageのリクエストに使用するJSON型のケースクラス
 *
 * @param saveLocal ローカルに保存するか否か
 * @param saveS3 Amazon S3に保存するか否か
 */
case class DatasetStorageParams(
  saveLocal: Option[Boolean] = None,
  saveS3: Option[Boolean] = None
)

/**
 * データセットのファイル検索系APIのリクエストに使用するJSON型のケースクラス
 *
 * 具体的には、以下のAPIで使用する。
 * GET /api/datasets/:datasetId/files
 * GET /api/datasets/:datasetId/files/:fileId/zippedfiles
 *
 * @param limit 検索件数上限
 * @param offset 検索位置
 */
case class SearchRangeParams(
  limit: Option[Int] = None,
  offset: Option[Int] = None
)

/**
 * GET /api/datasets/:datasetId/appsのリクエストに使用するJSON型のケースクラス
 *
 * @param excludeIds 検索から除外するアプリID
 * @param deletedType 論理削除されたアプリを検索対象とするか
 *  (@see dsmoq.services.DatasetService.GetAppDeletedTypes)
 * @param limit 検索件数上限
 * @param offset 検索位置
 */
case class SearchAppsParams(
  excludeIds: Seq[String] = Seq.empty,
  deletedType: Option[Int] = None,
  limit: Option[Int] = None,
  offset: Option[Int] = None
)

/**
 * PUT /api/datasets/:datasetId/apps/primaryのリクエストに使用するJSON型のケースクラス
 *
 * @param appId データセットに設定するAppID
 */
case class ChangePrimaryAppParams(
  appId: Option[String] = None
)

/**
 * POST /api/dataset_queriesのリクエストに使用するJSON型のケースクラス
 *
 * @param name クエリの名前
 * @param query 保存するクエリの検索条件
 */
case class CreateDatasetQueryParams(
  name: Option[String] = None,
  query: SearchDatasetCondition
)
