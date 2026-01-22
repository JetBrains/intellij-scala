package org.jetbrains.plugins.scala.compiler

import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ui.update.{MergingUpdateQueue, Update}

import java.util.concurrent.locks.{Lock, ReentrantLock}

@Service(Array(Service.Level.APP))
private final class CompileServerErrorNotificationService extends Disposable {

  private val errorNotificationUpdateQueue: MergingUpdateQueue =
    new MergingUpdateQueue("ErrorNotificationQueue", 1000, true, MergingUpdateQueue.ANY_COMPONENT, this)

  private val errorsBuffer: java.lang.StringBuilder = new java.lang.StringBuilder()

  private val errorsBufferLock: Lock = new ReentrantLock()

  private val showNotificationUpdate: Update = new Update(this) {
    override def run(): Unit = {
      errorsBufferLock.lock()
      val text = try {
        val t = errorsBuffer.toString
        errorsBuffer.setLength(0)
        t
      } finally {
        errorsBufferLock.unlock()
      }

      @NlsSafe
      val message = text.replace(System.lineSeparator(), "<br/>")
      CompileServerNotifications.showNotification(message, NotificationType.ERROR, project = None)
    }
  }

  override def dispose(): Unit = {}

  def onError(errorsText: String): Unit = {
    errorsBufferLock.lock()
    try errorsBuffer.append(errorsText)
    finally errorsBufferLock.unlock()
    errorNotificationUpdateQueue.queue(showNotificationUpdate)
  }
}

private object CompileServerErrorNotificationService {
  def instance(): CompileServerErrorNotificationService =
    ApplicationManager.getApplication.getService(classOf[CompileServerErrorNotificationService])
}
