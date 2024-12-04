package org.jetbrains.plugins.scala.components

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget.WidgetPresentation
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.actions.ToggleTypeAwareHighlightingAction
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import java.awt.event.MouseEvent
import javax.swing.Icon

private final class TypeAwareWidget(project: Project)
  extends StatusBarWidget with StatusBarWidget.IconPresentation {

  override def ID(): String = TypeAwareWidgetFactory.ID

  override def getPresentation: WidgetPresentation = this

  override def getIcon: Icon =
    if (isEnabled) Icons.TYPED else Icons.UNTYPED

  override def getTooltipText: String = {
    val title = ScalaBundle.message("type.aware.highlighting.title")

    val toChange = shortcutText match {
      case Some(text) => ScalaBundle.message("click.or.press.shortcut.to.change", text)
      case None => ScalaBundle.message("click.to.change")
    }

    val status = if (isEnabled) ScalaBundle.message("enabled.word") else ScalaBundle.message("disabled.word")
    //noinspection ScalaExtractStringToBundle
    s"$title: $status $toChange"
  }

  override def getClickConsumer: Consumer[MouseEvent] =
    _ => ToggleTypeAwareHighlightingAction.toggleSettingAndRehighlight(project)

  private def isEnabled: Boolean =
    ScalaProjectSettings.getInstance(project).isTypeAwareHighlightingEnabled

  private def shortcutText: Option[String] = {
    val action = ActionManager.getInstance().getAction("Scala.EnableErrors")
    action.getShortcutSet.getShortcuts.headOption.map(KeymapUtil.getShortcutText)
  }
}
