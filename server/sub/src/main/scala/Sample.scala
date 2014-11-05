import dsmoq.persistence._
import org.joda.time._

/**
 * Created by s.soyama on 2014/11/05.
 */
object Sample {
  def main(args: Array[String]) {
    Annotation("", "", "", DateTime.now, "", DateTime.now, None, None)
    println("hello!")
  }
}
