package dsmoq.taskServer

sealed class Command(val taskId: String, val datasetId: String, val withDelete: Boolean)

case class MoveToS3(override val taskId: String, override val datasetId: String, override val withDelete: Boolean) extends Command(taskId, datasetId, withDelete)

case class MoveToLocal(override val taskId: String, override val datasetId: String, override val withDelete: Boolean) extends Command(taskId, datasetId, withDelete)

case class DoNothing() extends Command("", "", false)

case class Delete(override val taskId: String, override val datasetId: String, val fileId: String) extends Command(taskId, datasetId, true)