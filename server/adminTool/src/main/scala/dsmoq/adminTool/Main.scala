package dsmoq.adminTool

object Main {

  def main(args: Array[String]): Unit = {
    args.toList match {
      case "list" :: Nil =>
      case "search" :: loginName :: Nil =>
      case "publish" :: loginName :: Nil =>
      case "remove" :: consumerKey :: Nil =>
      case _ => printUsage()
    }
  }

  private def printUsage(): Unit = {
    println(
      """usage:
        |adminTool list                      : List all consumer_key.
        |adminTool search <some login name>  : List all consumer_key related to login name user.
        |adminTool publish <some login name> : Publish consumer_key to login name user.
        |adminTool remove <consumer_key>     : Remove consumer_key.
      """.stripMargin)
  }

}
