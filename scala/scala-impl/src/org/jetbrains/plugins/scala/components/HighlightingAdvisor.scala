package org.jetbrains.plugins.scala
package components

import java.awt.event.MouseEvent

import com.intellij.ide.DataManager
import com.intellij.notification._
import com.intellij.openapi.actionSystem.{ActionManager, CommonDataKeys, DataContext}
import com.intellij.openapi.components._
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget.{PlatformType, WidgetPresentation}
import com.intellij.openapi.wm.{StatusBar, StatusBarWidget, WindowManager}
import com.intellij.util.{Consumer, FileContentUtil}
import javax.swing.Icon
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.util.NotificationUtil

import scala.collection.JavaConverters._

@State(
  name = "HighlightingAdvisor", storages = Array(
  new Storage("highlighting.xml"))
)
class HighlightingAdvisor(project: Project) extends AbstractProjectComponent(project) with PersistentStateComponent[HighlightingSettings] {
  @Language("HTML")
  private val AdviceMessage = """
  <html>
   <body>
   <p><a href="http://confluence.jetbrains.net/display/SCA/Type-aware+highlighting">Type-aware highlighting</a>
    helps to discover type mismatches, unresolved symbols and many other type-related errors.</p>
   <br>
   <p>However, the feature is in beta and sometimes may report "false errors" in regular code.</p>
   <br>
   <a href="ftp://enable">Enable type-aware highlighting</a> (recommended) or <a href="ftp://disable">leave it disabled</a>
   </body>
  </html>"""

  @Language("HTML")
  private val EnabledMessage = """
  <html>
   <body>
   <p><a href="http://confluence.jetbrains.net/display/SCA/Type-aware+highlighting">Type-aware highlighting</a> has been enabled.</p>
   <!--<br>
   <a href="ftp://disable">Disable it again</a>-->
   </body>
  </html>"""

  @Language("HTML")
  private val DisabledMessage = """
  <html>
   <body>
   <p><a href="http://confluence.jetbrains.net/display/SCA/Type-aware+highlighting">Type-aware highlighting</a> has been disabled.</p>
   <!--<br>
   <a href="ftp://enable">Enable it again</a>-->
   </body>
  </html>"""

  private var installed = false

  private var settings = new HighlightingSettings()

  override def getComponentName = "HighlightingAdvisor"

  override def projectOpened() {
    project.scalaEvents.addScalaProjectListener(ScalaListener)
    statusBar.foreach { bar =>
      configureWidget(bar)
      notifyIfNeeded()
    }
  }

  override def projectClosed() {
    project.scalaEvents.removeScalaProjectListener(ScalaListener)
    statusBar.foreach { bar =>
      configureWidget(bar)
    }
  }

  def getState: HighlightingSettings = settings

  def loadState(state: HighlightingSettings) {
    settings = state
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

  private def notifyIfNeeded() {
    if (settings.SUGGEST_TYPE_AWARE_HIGHLIGHTING && !enabled && applicable) {
      notify("Configure type-aware highlighting for the project", AdviceMessage, NotificationType.WARNING)
    }
  }

  private def notify(title: String, message: String, notificationType: NotificationType) {
    NotificationUtil.builder(project, message) setNotificationType notificationType setTitle title setHandler {
      case "enable" => enabled = true
      case "disable" => enabled = false
      case _ =>
    }
  }

  def toggle() {
    if (applicable) {
      enabled = !enabled
    }
  }

  private def applicable = project.hasScala && !project.hasDotty

  def enabled: Boolean = settings.TYPE_AWARE_HIGHLIGHTING_ENABLED

  private def enabled_=(enabled: Boolean) {
    settings.SUGGEST_TYPE_AWARE_HIGHLIGHTING = false

    if (this.enabled == enabled) return

    settings.TYPE_AWARE_HIGHLIGHTING_ENABLED = enabled

    statusBar.foreach { bar =>
      updateWidget(bar)
      reparseActiveFile()

      notify(status, if (enabled) EnabledMessage else DisabledMessage, NotificationType.INFORMATION)
    }
  }

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

  private object ScalaListener extends ScalaProjectListener {
    def onScalaProjectChanged() {
      statusBar.foreach { bar =>
        configureWidget(bar)
        if (applicable) {
          notifyIfNeeded()
        }
      }
    }
  }
}

object HighlightingAdvisor {
  def getInstance(project: Project): HighlightingAdvisor = project.getComponent(classOf[HighlightingAdvisor])
}
