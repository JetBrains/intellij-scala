package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.{StatusBar, StatusBarWidget, StatusBarWidgetFactory}
import com.intellij.util.messages.Topic
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings

import javax.swing.Icon

private final class CompileServerWidgetFactory extends StatusBarWidgetFactory {
  override def getId: String = CompileServerWidgetFactory.ID

  override def getDisplayName: String = ServerManagementBundle.message("scala.compile.server.title")

  override def isAvailable(project: Project): Boolean =
    CompileServerLauncher.running || canBeEnabled(project)

  override def createWidget(project: Project): StatusBarWidget = new CompileServerWidget(project)

  override def canBeEnabledOn(statusBar: StatusBar): Boolean = {
    val project = statusBar.getProject
    if (project == null) return false
    canBeEnabled(project)
  }

  override def isEnabledByDefault: Boolean = false

  private def canBeEnabled(project: Project): Boolean =
    ScalaCompileServerSettings.getInstance().COMPILE_SERVER_ENABLED && project.hasScala
}

private object CompileServerWidgetFactory {
  val ID: String = "ScalaCompileServer"

  val IconRunning: Icon = Icons.COMPILE_SERVER
  val IconStopped: Icon = IconLoader.getDisabledIcon(IconRunning)

  trait UpdateWidgetListener {
    def updateWidget(): Unit
  }

  val Topic: Topic[UpdateWidgetListener] =
    new Topic("Scala Compile Server widget update topic", classOf[UpdateWidgetListener])
}
