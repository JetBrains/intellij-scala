package org.jetbrains.plugins.scala.components

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import org.jetbrains.plugins.scala.extensions.executeOnPooledThread

object TypeAwareWidgetUpdater {
  def updateWidget(project: Project): Unit = executeOnPooledThread {
    if (project.isDisposed) return
    project.getService(classOf[StatusBarWidgetsManager]).updateWidget(classOf[TypeAwareWidgetFactory])
    val statusBar = WindowManager.getInstance().getStatusBar(project)
    if (statusBar eq null) return
    statusBar.updateWidget(TypeAwareWidgetFactory.ID)
  }
}
