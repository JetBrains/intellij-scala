package org.jetbrains.bsp

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.{StatusBar, StatusBarWidget, StatusBarWidgetFactory}
import com.intellij.util.messages.Topic

import javax.swing.Icon

private final class BspServerWidgetFactory extends StatusBarWidgetFactory {
  override def getId: String = BspServerWidgetFactory.ID

  override def getDisplayName: String = BspBundle.message("bsp.widget.bsp.connection")

  override def isAvailable(project: Project): Boolean = BspUtil.isBspProject(project)

  override def createWidget(project: Project): StatusBarWidget = new BspServerWidget()

  override def canBeEnabledOn(statusBar: StatusBar): Boolean = {
    val project = statusBar.getProject
    if (project == null) return false
    isAvailable(project)
  }
}

private object BspServerWidgetFactory {
  val ID: String = "BSP"

  val IconRunning: Icon = Icons.BSP
  val IconStopped: Icon = IconLoader.getDisabledIcon(IconRunning)
  val logger: Logger = Logger.getInstance(classOf[BspServerWidgetFactory])

  trait UpdateWidgetListener {
    def updateWidget(): Unit
  }

  val Topic: Topic[UpdateWidgetListener] =
    new Topic("Bsp server widget update topic", classOf[UpdateWidgetListener])
}
