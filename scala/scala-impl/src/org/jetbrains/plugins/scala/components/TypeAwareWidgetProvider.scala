package org.jetbrains.plugins.scala
package components

import java.awt.event.MouseEvent

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget.WidgetPresentation
import com.intellij.openapi.wm.{StatusBar, StatusBarWidget, StatusBarWidgetProvider, WindowManager}
import com.intellij.util.Consumer
import javax.swing.{Icon, Timer}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.actions.ToggleTypeAwareHighlightingAction
import org.jetbrains.plugins.scala.extensions.{ObjectExt, invokeLater}
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

class TypeAwareWidgetProvider extends StatusBarWidgetProvider {
  override def getWidget(project: Project): StatusBarWidget = new TypeAwareWidgetProvider.Widget(project)
}

object TypeAwareWidgetProvider {

  private class Widget(project: Project) extends StatusBarWidget with StatusBarWidget.IconPresentation {

    private var isApplicable = checkIsApplicable()

    private def isEnabled = ScalaProjectSettings.getInstance(project).isTypeAwareHighlightingEnabled

    private val myTimer = new Timer(1000, _ => {
      invokeLater(updateWidget())
    })

    override def ID: String = "TypeAwareHighlighting"

    override def getPresentation: WidgetPresentation = this

    override def install(statusBar: StatusBar): Unit = {
      myTimer.setRepeats(true)
      myTimer.start()
      subscribeToRootsChange()
    }

    override def dispose(): Unit = {
      myTimer.stop()
    }

    @Nullable
    override def getIcon: Icon = {
      if (!isApplicable) null
      else if (isEnabled) Icons.TYPED else Icons.UNTYPED
    }

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

    private def statusBar: Option[StatusBar] =
      Option(WindowManager.getInstance).flatMap(_.getStatusBar(project).toOption)

    private def updateWidget(): Unit = {
      statusBar.foreach(_.updateWidget(ID))
    }

    private def checkIsApplicable(): Boolean = project.isOpen && project.hasScala

    private def subscribeToRootsChange(): Unit = {
      project.subscribeToModuleRootChanged() { _ =>
        invokeLater {
          isApplicable = checkIsApplicable()
          updateWidget()
        }
      }
    }
  }
}

