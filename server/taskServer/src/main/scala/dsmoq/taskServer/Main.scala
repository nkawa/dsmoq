package dsmoq.taskServer

import org.json4s._
import org.json4s.jackson.JsonMethods
import org.json4s.{DefaultFormats, Formats}
import scalikejdbc._
import dsmoq.persistence.Task
import akka.actor.{ActorSystem, Props}
import scala.concurrent.duration._
import scalikejdbc.config._

object Main {
  private implicit val jsonFormats: Formats = DefaultFormats

  def main(args: Array[String]) {
    DBs.setupAll()
    val system = ActorSystem()
    import system.dispatcher
    val taskActor = system.actorOf(Props[TaskActor], "TaskActor")

    system.scheduler.schedule(0 milliseconds, 5 minutes) {
      val tasks = getNewTasks().map(x => (x.id, JsonMethods.parse(x.parameter).extract[TaskParameter]))
      val commands = tasks.map(x => datasetToCommand(x._1, x._2))
      for (command <- commands) {
        taskActor ! command
      }
    }
  }

  def getNewTasks(): List[Task] = {
    DB readOnly { implicit s =>
      val t = Task.t
      withSQL {
        select
          .from(Task as t)
          .where
            .eq(t.status, 0)
          .orderBy(t.createdAt)
          .limit(50)
      }.map(Task(t)).list().apply()
    }
  }

  def datasetToCommand(taskId:String, param: TaskParameter): Command = {
    param.taskType match {
      case 0 => MoveToS3(taskId, param.datasetId, param.withDelete.getOrElse(false))
      case 1 => MoveToLocal(taskId, param.datasetId, param.withDelete.getOrElse (false) )
      case 2 => Delete(taskId, param.datasetId, param.fileId.get)
      case _ => DoNothing()
    }
  }

  case class TaskParameter(taskType: Int, datasetId: String, withDelete: Option[Boolean], fileId: Option[String])
}


