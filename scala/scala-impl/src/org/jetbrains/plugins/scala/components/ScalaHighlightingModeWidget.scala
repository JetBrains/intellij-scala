package org.jetbrains.plugins.scala.components

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget.WidgetPresentation
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.ScalaBundle.message
import org.jetbrains.plugins.scala.actions.ScalaHighlightingModeAction
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import java.awt.event.MouseEvent
import javax.swing.Icon

private final class ScalaHighlightingModeWidget(project: Project)
  extends StatusBarWidget with StatusBarWidget.IconPresentation {

  override def ID(): String = ScalaHighlightingModeWidgetFactory.ID

  override def getPresentation: WidgetPresentation = this

  override def getIcon: Icon = Icons.HIGHLIGHTING_MODE

  override def getTooltipText: String = {
    type Mode = Boolean

    val settings = ScalaProjectSettings.in(project)

    val modeScala2: Mode = settings.isCompilerHighlightingScala2
    val modeScala3: Mode = settings.isCompilerHighlightingScala3

    val mode: Option[Mode] = {
      val differ = project.hasScala2 && project.hasScala3 && modeScala2 != modeScala3
      if (differ) None
      else if (project.hasScala2) Some(modeScala2)
      else Some(modeScala3)
    }

    def optionText(mode: Mode) = if (mode) "✓" else "✗"

    val builtInOptions =
      s"<tr><td>${message("scala.highlighting.mode.widget.type.aware")}:</td><td style='margin-left: 7px;'>${optionText(settings.isTypeAwareHighlightingEnabled)}</td></tr>" +
        s"<tr><td>${message("scala.highlighting.mode.widget.incremental")}:</td><td style='margin-left: 7px;'>${optionText(settings.isIncrementalHighlighting)}</td></tr>"

    val builtInInspections = !(mode.contains(true) && settings.isDisableInspections)

    val compilerOptions =
      s"<tr><td>${message("scala.highlighting.mode.widget.built.in.inspections")}:</td><td style='margin-left: 7px;'>${optionText(builtInInspections)}</td></tr>" +
        s"<tr><td>${message("scala.highlighting.mode.widget.compiler.types")}:</td><td style='margin-left: 7px;'>${optionText(settings.isUseCompilerTypes)}</td></tr>" +
        s"<tr><td>${message("scala.highlighting.mode.widget.compiler.delay")}:</td><td style='margin-left: 7px;'>${settings.getCompilerHighlightingDelay} ${message("scala.highlighting.mode.widget.compiler.ms")}</td></tr>"

    def modeText(mode: Mode) = if (mode) message("type.checker.compiler") else message("type.checker.built.in")

    def optionsFor(mode: Mode) = if (mode) compilerOptions else builtInOptions

    val info = mode.map { mode =>
      s"<div>${modeText(mode)}</div>" +
        "<table cellpadding='0' cellspacing='0' style='margin-left: 10px;'>" + optionsFor(mode) + "</table>"
    } getOrElse {
      s"<div>${message("type.checker.label.scala2")} ${modeText(modeScala2)}</div>" +
        "<table cellpadding='0', cellspacing='0' style='margin-left: 10px;'>" + optionsFor(modeScala2) + "</table>" +
      s"<div style='margin-top: 3px;'>${message("type.checker.label.scala3")} ${modeText(modeScala3)}</div>" +
        "<table cellpadding='0', cellspacing='0' style='margin-left: 10px;'>"+ optionsFor(modeScala3) + "</table>"
    }

    s"<html><body><div style='padding: 3px;'>" +
      s"<strong>${message("scala.highlighting.mode")}</strong>" +
      s"<div style='margin-top: 7px'>$info</div>" +
      s"</div></body></html>"
  }

  override def getClickConsumer: Consumer[MouseEvent] =
    _ => ScalaHighlightingModeAction.perform(project)
}
