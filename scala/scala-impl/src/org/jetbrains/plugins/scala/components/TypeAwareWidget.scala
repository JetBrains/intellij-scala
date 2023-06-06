package org.jetbrains.plugins.scala.components

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget.WidgetPresentation
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.openapi.wm.{StatusBar, StatusBarWidget}
import com.intellij.util.Consumer
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.actions.ToggleTypeAwareHighlightingAction
import org.jetbrains.plugins.scala.extensions.invokeLater
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import java.awt.event.MouseEvent
import javax.swing.Icon

private final class TypeAwareWidget(project: Project, factory: TypeAwareWidgetFactory)
  extends StatusBarWidget
    with StatusBarWidget.IconPresentation
    with TypeAwareWidgetFactory.UpdateListener {

  private var statusBar: StatusBar = _
  private val connection: MessageBusConnection = project.getMessageBus.connect()
  private val widgetsManager: StatusBarWidgetsManager = project.getService(classOf[StatusBarWidgetsManager])

  override def ID(): String = TypeAwareWidgetFactory.ID

  override def install(statusBar: StatusBar): Unit = {
    this.statusBar = statusBar
    connection.subscribe(TypeAwareWidgetFactory.Topic, this)
    subscribeToRootsChange()
  }

  override def dispose(): Unit = {
    connection.dispose()
    statusBar = null
  }

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

  override def updateWidget(): Unit = {
    widgetsManager.updateWidget(factory)
    if (statusBar ne null) {
      statusBar.updateWidget(ID())
    }
  }

  private def isEnabled: Boolean =
    ScalaProjectSettings.getInstance(project).isTypeAwareHighlightingEnabled

  private def shortcutText: Option[String] = {
    val action = ActionManager.getInstance().getAction("Scala.EnableErrors")
    action.getShortcutSet.getShortcuts.headOption.map(KeymapUtil.getShortcutText)
  }

  private def subscribeToRootsChange(): Unit = {
    project.subscribeToModuleRootChanged() { _ =>
      invokeLater {
        updateWidget()
      }
    }
  }
}
