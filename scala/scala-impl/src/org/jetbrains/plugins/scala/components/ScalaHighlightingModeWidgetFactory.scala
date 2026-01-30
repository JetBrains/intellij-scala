package org.jetbrains.plugins.scala.components

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.{StatusBar, StatusBarWidget, StatusBarWidgetFactory}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.project.ProjectExt

private final class ScalaHighlightingModeWidgetFactory extends StatusBarWidgetFactory {
  override def getId: String = ScalaHighlightingModeWidgetFactory.ID

  override def getDisplayName: String = ScalaBundle.message("scala.highlighting.mode")

  override def isAvailable(project: Project): Boolean = project.isOpen && project.hasScala

  override def createWidget(project: Project): StatusBarWidget = new ScalaHighlightingModeWidget(project)

  override def canBeEnabledOn(statusBar: StatusBar): Boolean = {
    val project = statusBar.getProject
    if (project == null) return false
    isAvailable(project)
  }
}

private object ScalaHighlightingModeWidgetFactory {
  val ID: String = "ScalaHighlightingMode"
}
