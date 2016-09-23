package dsmoq.services.json

import org.json4s.CustomSerializer
import org.json4s.Formats
import org.json4s.JArray
import org.json4s.JDecimal
import org.json4s.JDouble
import org.json4s.JInt
import org.json4s.JNull
import org.json4s.JObject
import org.json4s.JString
import org.json4s.JValue

/** SearchCondition形式のデータセット検索条件を表すトレイト */
sealed trait SearchDatasetCondition

/** SearchCondition形式のデータセット検索条件のコンパニオンオブジェクト */
object SearchDatasetCondition {
  /**
   * and/orを表すデータセット検索条件
   *
   * @param operator and/or
   * @param value 要素
   */
  case class Container(
    operator: Operators.Container,
    value: Seq[SearchDatasetCondition]
  ) extends SearchDatasetCondition

  /**
   * query検索を表すデータセット検索条件
   *
   * @param query 検索文字列
   * @param contains 検索文字列を含む検索の場合true、検索文字列を含まない検索の場合false
   */
  case class Query(query: String = "", contains: Boolean = true) extends SearchDatasetCondition

  /**
   * owner検索を表すデータセット検索条件
   *
   * @param name 検索するowner名
   * @param equals 指定ownerが所有権を持つ検索の場合true、所有権を持たない検索の場合false
   */
  case class Owner(name: String, equals: Boolean = true) extends SearchDatasetCondition

  /**
   * タグ検索を表すデータセット検索条件
   *
   * @param tag 検索するタグ
   */
  case class Tag(tag: String) extends SearchDatasetCondition

  /**
   * 属性検索を表すデータセット検索条件
   *
   * @param key 属性名
   * @param value 属性値
   */
  case class Attribute(key: String = "", value: String = "") extends SearchDatasetCondition

  /**
   * ファイルサイズ検索を表すデータセット検索条件
   *
   * @param operator 比較子
   * @param value 値
   * @param unit 単位
   */
  case class TotalSize(
    operator: Operators.Compare = Operators.Compare.GE,
    value: Double = 0,
    unit: SizeUnit = SizeUnit.Byte
  ) extends SearchDatasetCondition

  /**
   * ファイル数検索を表すデータセット検索条件
   *
   * @param operator 比較子
   * @param value 値
   */
  case class NumOfFiles(
    operator: Operators.Compare = Operators.Compare.GE,
    value: Int = 0
  ) extends SearchDatasetCondition

  /**
   * 公開/非公開検索を表すデータセット検索条件
   *
   * @param public 公開されているデータセットを検索する場合true、非公開の場合false
   */
  case class Public(public: Boolean = true) extends SearchDatasetCondition

  object Operators {
    sealed trait Container
    object Container {
      case object AND extends Container
      case object OR extends Container
    }
    /** 比較子を表すトレイト */
    sealed trait Compare
    object Compare {
      /** 以上を表す比較子 */
      case object GE extends Compare
      /** 以下を表す比較子 */
      case object LE extends Compare
    }
  }

  /** ファイルサイズ単位を表すトレイト */
  sealed trait SizeUnit {
    /** 値に対する倍率 */
    def magnification: Double
  }
  object SizeUnit {
    case object Byte extends SizeUnit {
      val magnification = 1D
    }
    case object KB extends SizeUnit {
      val magnification = 1024D
    }
    case object MB extends SizeUnit {
      val magnification = 1024D * 1024
    }
    case object GB extends SizeUnit {
      val magnification = 1024D * 1024 * 1024
    }
  }

  /**
   * 検索条件をJSON値に変換する。
   *
   * @param x 検索条件
   * @return JSON値
   */
  def toJson(x: SearchDatasetCondition): JValue = {
    x match {
      case Container(op, xs) => {
        val operator = op match {
          case Operators.Container.AND => JString("and")
          case Operators.Container.OR => JString("or")
        }
        JObject(
          List(
            "operator" -> operator,
            "value" -> JArray(xs.map(toJson).toList)
          )
        )
      }
      case Query(value, contains) => {
        JObject(
          List(
            "target" -> JString("query"),
            "operator" -> JString(if (contains) "contain" else "not-contain"),
            "value" -> JString(value)
          )
        )
      }
      case Owner(value, equals) => {
        JObject(
          List(
            "target" -> JString("owner"),
            "operator" -> JString(if (equals) "equal" else "not-equal"),
            "value" -> JString(value)
          )
        )
      }
      case Tag(value) => {
        JObject(
          List(
            "target" -> JString("tag"),
            "value" -> JString(value)
          )
        )
      }
      case Attribute(key, value) => {
        JObject(
          List(
            "target" -> JString("attribute"),
            "key" -> JString(key),
            "value" -> JString(value)
          )
        )
      }
      case TotalSize(op, value, u) => {
        val operator = op match {
          case Operators.Compare.GE => JString("ge")
          case Operators.Compare.LE => JString("le")
        }
        val unit = u match {
          case SizeUnit.Byte => JString("byte")
          case SizeUnit.KB => JString("kb")
          case SizeUnit.MB => JString("mb")
          case SizeUnit.GB => JString("gb")
        }
        JObject(
          List(
            "target" -> JString("total-size"),
            "operator" -> operator,
            "unit" -> unit,
            "value" -> JDouble(value)
          )
        )
      }
      case NumOfFiles(op, value) => {
        val operator = op match {
          case Operators.Compare.GE => JString("ge")
          case Operators.Compare.LE => JString("le")
        }
        JObject(
          List(
            "target" -> JString("num-of-files"),
            "operator" -> operator,
            "value" -> JInt(value)
          )
        )
      }
      case Public(public) => {
        JObject(
          List(
            "target" -> JString("public"),
            "value" -> JString(if (public) "public" else "private")
          )
        )
      }
    }
  }

  /**
   * JSON値を検索条件に変換する。
   *
   * @param x JSON値
   * @return 変換結果
   */
  def unapply(x: JValue)(implicit format: Formats): Option[SearchDatasetCondition] = {
    x match {
      case JObject(fs) => {
        val fields = fs.toMap
        val t = fields.getOrElse("target", JNull)
        val op = fields.getOrElse("operator", JNull)
        val v = fields.getOrElse("value", JNull)
        (t, op, v) match {
          case (_, JString("or"), JArray(xs)) => {
            sequence(xs.map(unapply)).map { value =>
              Container(Operators.Container.OR, value)
            }
          }
          case (_, JString("and"), JArray(xs)) => {
            sequence(xs.map(unapply)).map { value =>
              Container(Operators.Container.AND, value)
            }
          }
          case (JString("query"), _, JString(value)) => {
            Some(Query(value, op != JString("not-contain")))
          }
          case (JString("owner"), _, JString(value)) => {
            Some(Owner(value, op != JString("not-equal")))
          }
          case (JString("tag"), _, JString(value)) => {
            Some(Tag(value))
          }
          case (JString("attribute"), _, _) => {
            val key = fields.get("key").collect { case JString(x) => x }.getOrElse("")
            val value = v match {
              case JString(x) => x
              case _ => ""
            }
            Some(Attribute(key, value))
          }
          case (JString("total-size"), _, _) => {
            val value = v.extractOpt[Double].getOrElse(0D)
            val operator = if (op == JString("le")) Operators.Compare.LE else Operators.Compare.GE
            val unit = fields.get("unit").collect {
              case JString("kb") => SizeUnit.KB
              case JString("mb") => SizeUnit.MB
              case JString("gb") => SizeUnit.GB
            }.getOrElse(SizeUnit.Byte)
            Some(TotalSize(operator, value, unit))
          }
          case (JString("num-of-files"), _, _) => {
            val value = v.extractOpt[Int].getOrElse(0)
            val operator = if (op == JString("le")) Operators.Compare.LE else Operators.Compare.GE
            Some(NumOfFiles(operator, value))
          }
          case (JString("public"), _, _) => {
            Some(Public(v != JString("private")))
          }
          case _ => None
        }
      }
      case _ => None
    }
  }

  /**
   * Seq[Option[T]] を Option[Seq[A]] に変換する。
   *
   * @param xs 変換元
   * @return 変換結果
   */
  def sequence[A](xs: Seq[Option[A]]): Option[Seq[A]] = {
    val ret = xs.flatten
    if (ret.size == xs.size) Some(ret) else None
  }
}

/** SearchDatasetCondition用のJSONカスタムシリアライザ */
object SearchDatasetConditionSerializer extends CustomSerializer[SearchDatasetCondition](implicit formats => (
  {
    case SearchDatasetCondition(p) => p
  },
  {
    case x: SearchDatasetCondition => SearchDatasetCondition.toJson(x)
  }
))
