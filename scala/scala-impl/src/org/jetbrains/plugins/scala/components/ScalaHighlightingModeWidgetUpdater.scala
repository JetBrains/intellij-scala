package org.jetbrains.plugins.scala.components

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import org.jetbrains.plugins.scala.extensions.executeOnPooledThread

object ScalaHighlightingModeWidgetUpdater {

  /**
   * @note Can be called on any thread. The updating logic will be run on a background, non-ui thread.
   */
  def scheduleWidgetUpdate(project: Project): Unit = executeOnPooledThread {
    updateWidgetImpl(project)
  }

  private def updateWidgetImpl(project: Project): Unit = {
    if (project.isDisposed)
      return
    project.getService(classOf[StatusBarWidgetsManager]).updateWidget(classOf[ScalaHighlightingModeWidgetFactory])
    val statusBar = WindowManager.getInstance().getStatusBar(project)
    if (statusBar eq null)
      return
    statusBar.updateWidget(ScalaHighlightingModeWidgetFactory.ID)
  }
}
