package dsmoq.tagManager

import java.util.UUID

import dsmoq.persistence._
import scalikejdbc._
import scalikejdbc.config.DBs
import org.joda.time.DateTime

object Main {
  val systemUserId = "dccc110c-c34f-40ed-be2c-7e34a9f1b8f0"

  def main(args: Array[String]): Unit = {
    try {
      DBs.setup()
      args.toList match {
        case "list" :: Nil => list()
        case "add" :: tagName :: colorCode ::Nil => addTag(tagName, colorCode)
        case "change" :: tagName :: colorCode :: Nil => updateTag(tagName, colorCode)
        case "remove" :: tagName :: Nil => removeTag(tagName)
        case _ => printUsage()
      }
    } catch {
      case e: Exception => {
        println("エラーが発生したため、プログラムを終了します：")
        e.printStackTrace()
      }
    }
  }

  def list(): Unit = {
    DB readOnly { implicit s =>
      val t = Tag.t
      val tags = withSQL {
        select
          .from(Tag as t)
          .where
            .isNull(t.deletedBy)
            .and
            .isNull(t.deletedAt)
            .orderBy(t.tag)
      }.map(Tag(t.resultName)).list.apply()
      println("%s found.".format(tags.size))
      println("tag name/color code")
      tags.map(x => x.tag + "/" + x.color).foreach(println)
    }
  }

  def addTag(tagName: String, colorCode: String) = {
    DB localTx { implicit s =>
      val t = Tag.t
      val tag = withSQL {
        select.from(Tag as t).where.eq(t.tag, tagName).limit(1)
      }.map(Tag(t.resultName)).single.apply()

      tag match {
        case Some(t) => {
          println("同名のタグがすでにあります。")
          println("tag name/color code")
          println("%s/%s".format(t.tag, t.color))
        }
        case None => {
          val timestamp = DateTime.now
          val tag = Tag.create(
            id = UUID.randomUUID().toString,
            tag = tagName,
            color = colorCode,
            createdBy = systemUserId,
            createdAt = timestamp,
            updatedBy = systemUserId,
            updatedAt = timestamp
          )
          println("tag name/color code")
          println("%s/%s".format(tag.tag, tag.color))
        }
      }
    }
  }

  def updateTag(tagName: String, colorCode: String) = {
    DB localTx { implicit s =>
      val t = Tag.t
      val tag = withSQL {
        select.from(Tag as t).where.eq(t.tag, tagName).limit(1)
      }.map(Tag(t.resultName)).single.apply()

      tag match {
        case Some(t) => {
          val timestamp = DateTime.now
          withSQL {
            val c = Tag.column
            update(Tag)
              .set(c.color -> colorCode, c.updatedAt -> timestamp)
              .where
                .eq(c.tag, tagName)
          }
        }
        case None => println("指定したタグは存在しません。")
      }
    }
  }

  def removeTag(tagName: String) = {
    DB localTx { implicit s =>
      val t = Tag.t
      val tag = withSQL {
        select.from(Tag as t).where.eq(t.tag, tagName).limit(1)
      }.map(Tag(t.resultName)).single.apply()

      tag match {
        case Some(t) => {
          t.destroy()
          println("タグ[%s]の設定を削除しました".format(t.tag))
        }
        case None => println("指定したタグは存在しません。")
      }
    }
  }

  private def printUsage(): Unit = {
    println(
      """usage:
        |tagManager list                           : タグの一覧を表示します.
        |tagManager add <tag name> <color code>    : 指定したタグの色設定を追加します。
        |tagManager change <tag name> <color code> : 指定したタグの色設定を変更します。
        |tagManager remove <tag name>              : 指定したタグの色設定を削除します。
      """.stripMargin)
  }

}
