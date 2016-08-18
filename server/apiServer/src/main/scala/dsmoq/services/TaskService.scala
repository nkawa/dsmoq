package dsmoq.services

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import dsmoq.exceptions.NotFoundException
import dsmoq.persistence
import dsmoq.services.json.TaskData.TaskStatus
import scalikejdbc.DB

/**
 * データセットタスクの処理を取り扱うサービスオブジェクト
 */
object TaskService {

  /**
   * タスクのステータスを取得する。
   * @param taskId タスクID
   * @return
   *        Success(TaskStatus) タスクのステータス情報
   *        Failure(NullPointerException) 引数がnullの場合
   *        Failure(NotFoundException) タスクが見つからない場合
   */
  def getStatus(taskId: String): Try[TaskStatus] = {
    try {
      CheckUtil.checkNull(taskId, "taskId")
      DB readOnly { implicit s =>
        val task = persistence.Task.find(taskId) match {
          case None => throw new NotFoundException
          case Some(x) => x
        }
        Success(TaskStatus(task.status, task.createdBy, task.createdAt.toString()))
      }
    } catch {
      case e: Throwable => Failure(e)
    }
  }
}
