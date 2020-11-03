package org.jetbrains.plugins.scala
package components

import java.awt.event.MouseEvent

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget.WidgetPresentation
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.openapi.wm.{StatusBar, StatusBarWidget, StatusBarWidgetFactory}
import com.intellij.util.Consumer
import javax.swing.{Icon, Timer}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.actions.ToggleTypeAwareHighlightingAction
import org.jetbrains.plugins.scala.components.TypeAwareWidgetFactory.Widget
import org.jetbrains.plugins.scala.extensions.invokeLater
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

class TypeAwareWidgetFactory extends StatusBarWidgetFactory {
  override def getId: String = "TypeAwareHighlighting"

  override def getDisplayName: String = ScalaBundle.message("scala.type.aware.highlighting.indicator")

  override def isAvailable(project: Project): Boolean = project.isOpen && project.hasScala

  override def createWidget(project: Project): StatusBarWidget = new Widget(project, this)

  override def disposeWidget(widget: StatusBarWidget): Unit = ()

  override def canBeEnabledOn(statusBar: StatusBar): Boolean = isAvailable(statusBar.getProject)
}

object TypeAwareWidgetFactory {
  private class Widget(project: Project, factory: TypeAwareWidgetFactory) extends StatusBarWidget with StatusBarWidget.IconPresentation {
    private val statusBarWidgetsManager = project.getService(classOf[StatusBarWidgetsManager])
    private var statusBar = Option.empty[StatusBar]
    private def isEnabled = ScalaProjectSettings.getInstance(project).isTypeAwareHighlightingEnabled

    private val myTimer = new Timer(1000, _ => {
      invokeLater(updateWidget())
    })

    override def ID: String = "TypeAwareHighlighting"

    override def getPresentation: WidgetPresentation = this

    override def install(statusBar: StatusBar): Unit = {
      this.statusBar = Some(statusBar)
      myTimer.setRepeats(true)
      myTimer.start()
      subscribeToRootsChange()
    }

    override def dispose(): Unit = {
      myTimer.stop()
      statusBar = None
    }

    @Nullable
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

    override def getClickConsumer: Consumer[MouseEvent] = _ => {
      ToggleTypeAwareHighlightingAction.toggleSettingAndRehighlight(project)
      updateWidget()
    }

    private def shortcutText: Option[String] = {
      val action = ActionManager.getInstance().getAction("Scala.EnableErrors")
      action.getShortcutSet.getShortcuts.headOption.map(KeymapUtil.getShortcutText)
    }

    private def updateWidget(): Unit = {
      statusBarWidgetsManager.updateWidget(factory)
      statusBar.foreach(_.updateWidget(ID))
    }

    private def subscribeToRootsChange(): Unit = {
      project.subscribeToModuleRootChanged() { _ =>
        invokeLater {
          updateWidget()
        }
      }
    }
  }
}

