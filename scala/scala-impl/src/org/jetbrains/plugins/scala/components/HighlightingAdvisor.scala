package org.jetbrains.plugins.scala
package components

import java.awt.event.MouseEvent

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.{ActionManager, CommonDataKeys, DataContext}
import com.intellij.openapi.components._
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget.{PlatformType, WidgetPresentation}
import com.intellij.openapi.wm.{StatusBar, StatusBarWidget, WindowManager}
import com.intellij.util.{Consumer, FileContentUtil}
import javax.swing.Icon
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.collection.JavaConverters._

final class HighlightingAdvisor(project: Project) extends ProjectComponent {

  private var installed = false

  private def settings = ScalaProjectSettings.getInstance(project)

  project.subscribeToModuleRootChanged() { _ =>
    statusBar.foreach { bar =>
      configureWidget(bar)
    }
  }

  override def getComponentName = "HighlightingAdvisor"

  override def projectOpened(): Unit = {
    statusBar.foreach { bar =>
      configureWidget(bar)
    }
  }

  override def projectClosed(): Unit =
    statusBar.foreach { bar =>
      configureWidget(bar)
    }

  private def configureWidget(bar: StatusBar) {
    (applicable, installed) match {
      case (true, true) => // do nothing
      case (true, false) =>
        bar.addWidget(Widget, project)
        installed = true
      case (false, true) =>
        bar.removeWidget(Widget.ID)
        installed = false
      case (false, false) => // do nothing
    }
  }

  def toggle() {
    if (applicable) {
      settings.toggleTypeAwareHighlighting()

      statusBar.foreach { bar =>
        updateWidget(bar)
        reparseActiveFile()
      }
    }
  }

  private def applicable = project.hasScala

  def enabled: Boolean = settings.isTypeAwareHighlightingEnabled

  private def status = s"Scala type-aware highlighting: ${if (enabled) "enabled" else "disabled"}"

  private def updateWidget(bar: StatusBar) {
    bar.updateWidget(Widget.ID)
  }

  private def reparseActiveFile() {
    val context = DataManager.getInstance.getDataContextFromFocusAsync
    context.onSuccess((dataContext: DataContext) => {
      CommonDataKeys.EDITOR_EVEN_IF_INACTIVE.getData(dataContext) match {
        case editor: EditorEx =>
          FileContentUtil.reparseFiles(project, Seq(editor.getVirtualFile).asJavaCollection, true)
        case _                => // do nothing
      }
    })
  }

  private def statusBar: Option[StatusBar] =
    Option(WindowManager.getInstance).flatMap(_.getStatusBar(project).toOption)

  private object Widget extends StatusBarWidget {
    def ID = "TypeAwareHighlighting"

    override def getPresentation(platformType: PlatformType): WidgetPresentation = Presentation

    override def install(statusBar: StatusBar): Unit = {}

    override def dispose() {}

    object Presentation extends StatusBarWidget.IconPresentation {
      def getIcon: Icon = if (enabled) Icons.TYPED else Icons.UNTYPED

      override def getClickConsumer: Consumer[MouseEvent] = ClickConsumer

      override def getTooltipText: String = {
        val action = ActionManager.getInstance().getAction("Scala.EnableErrors")
        val shortcut = action.getShortcutSet.getShortcuts.headOption.map(KeymapUtil.getShortcutText)
        val orPressShortcut = shortcut.map(sh => s" or press $sh ").getOrElse(" ")
        val change = if (enabled) "disable" else "enable"

        s"$status (click${orPressShortcut}to $change)"
      }

      object ClickConsumer extends Consumer[MouseEvent] {
        def consume(t: MouseEvent): Unit = toggle()
      }
    }
  }
}

object HighlightingAdvisor {
  def getInstance(project: Project): HighlightingAdvisor = project.getComponent(classOf[HighlightingAdvisor])
}
