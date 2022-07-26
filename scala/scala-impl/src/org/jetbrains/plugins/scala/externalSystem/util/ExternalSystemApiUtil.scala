package org.jetbrains.plugins.scala.externalSystem.util

import com.intellij.openapi.application.{ApplicationManager, ModalityState, TransactionGuard}

/**
 * Reimplementation of deprecated APIs in [[com.intellij.openapi.externalSystem.util]],
 * namely `com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange` and
 * [[com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil]].
 */
object ExternalSystemApiUtil {
  def executeProjectChangeAction(task: DisposeAwareProjectChange): Unit =
    executeProjectChangeAction(synchronous = true, task)

  def executeProjectChangeAction(synchronous: Boolean, task: DisposeAwareProjectChange): Unit = {
    if (!ApplicationManager.getApplication.isDispatchThread) {
      TransactionGuard.getInstance().assertWriteSafeContext(ModalityState.defaultModalityState())
    }
    executeOnEdt(synchronous, () => ApplicationManager.getApplication.runWriteAction(task))
  }

  def executeOnEdt(synchronous: Boolean, task: Runnable): Unit = {
    val app = ApplicationManager.getApplication
    if (app.isDispatchThread) {
      task.run()
    } else if (synchronous) {
      app.invokeAndWait(task)
    } else {
      app.invokeLater(task)
    }
  }
}
