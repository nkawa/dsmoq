
object Main {
  def main(args: Array[String]) {

  }


}

sealed case class Move

case class MoveToS3 extends Move

case class MoveToLocal extends Move