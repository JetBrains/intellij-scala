package org.jetbrains.plugins.scala.components

import org.intellij.lang.annotations.Language
import javax.swing.event.HyperlinkEvent
import com.intellij.notification.{NotificationListener, NotificationType, Notification, Notifications}
import org.jetbrains.plugins.scala.DesktopUtils
import com.intellij.openapi.components.ProjectComponent
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.{StatusBarWidget, WindowManager, StatusBar}
import com.intellij.openapi.wm.StatusBarWidget.PlatformType
import org.jetbrains.plugins.scala.icons.Icons
import java.awt.event.MouseEvent
import com.intellij.ide.DataManager
import com.intellij.util.{FileContentUtil, Consumer}
import collection.JavaConversions._
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.config.ScalaFacet
import com.intellij.facet.{ProjectWideFacetAdapter, ProjectWideFacetListenersRegistry}
import com.intellij.openapi.actionSystem.{DataContext, PlatformDataKeys}
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.AsyncResult

class HighlightingAdvisor(project: Project) extends ProjectComponent {
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

  def getComponentName = "HighlightingAdvisor"

  def initComponent {}

  def disposeComponent {}

  def projectOpened {
    registry.registerListener(ScalaFacet.Id, FacetListener)
    configureWidget()
    notifyIfNeeded()
  }

  def projectClosed {
    registry.unregisterListener(ScalaFacet.Id, FacetListener)
    configureWidget()
  }

  private def configureWidget() {
    (applicable, installed) match {
      case (true, true) => // do nothing
      case (true, false) => {
        bar.addWidget(Widget, project)
        installed = true
      }
      case (false, true) => {
        bar.removeWidget(Widget.ID)
        installed = false
      }
      case (false, false) => // do nothing
    }
  }

  private def notifyIfNeeded() {
    if(settings.SUGGEST_ERROR_HIGHLIGHTING && !enabled && applicable) {
      notify("Configure type-aware highlighting for the project", AdviceMessage, NotificationType.WARNING)
    }
  }

  private def notify(title: String, message: String, notificationType: NotificationType) {
    val notification = new Notification("scala", title, message, notificationType, HyperlinkListener)
    Notifications.Bus.notify(notification, project)
  }

  private def registry: ProjectWideFacetListenersRegistry =
    ProjectWideFacetListenersRegistry.getInstance(project)

  def toggle() {
    if(applicable) {
      enabled = !enabled
    }
  }

  private def applicable = ScalaFacet.isPresentIn(project)

  private def enabled = settings.ENABLE_ERROR_HIGHLIGHTING

  private def enabled_=(enabled: Boolean) {
    if(settings.SUGGEST_ERROR_HIGHLIGHTING) {
      settings.SUGGEST_ERROR_HIGHLIGHTING = false
      ApplicationManager.getApplication.saveSettings()
    }

    if(this.enabled == enabled) return

    settings.ENABLE_ERROR_HIGHLIGHTING = enabled
    project.save()

    updateWidget()
    reparseActiveFile()

    if (enabled)
      notify(status, EnabledMessage, NotificationType.INFORMATION)
    else
      notify(status, DisabledMessage, NotificationType.INFORMATION)
  }

  private def status = "Type-aware highlighting: %s"
          .format(if(enabled) "enabled" else "disabled")

  private def updateWidget() {
    bar.updateWidget(Widget.ID)
  }

  private def reparseActiveFile() {
    val context = DataManager.getInstance.getDataContextFromFocus
    context.doWhenDone(new AsyncResult.Handler[DataContext]() {
      def run(v: DataContext) {
        PlatformDataKeys.EDITOR_EVEN_IF_INACTIVE.getData(v) match {
          case editor: EditorEx => {
            FileContentUtil.reparseFiles(project, Seq(editor.getVirtualFile), true)
          }
          case _ => // do nothing
        }
      }
    })
  }

  private def settings = ScalaCodeStyleSettings.getInstance(project)

  private def bar = WindowManager.getInstance().getStatusBar(project)

  private object HyperlinkListener extends NotificationListener {
    def hyperlinkUpdate(notification: Notification, event: HyperlinkEvent) {
      event match {
        case Link(url) => DesktopUtils.browse(url)
        case Action("enable") => {
          notification.expire()
          enabled = true
        }
        case Action("disable") => {
          notification.expire()
          enabled = false
        }
      }
    }
  }

  private object Link {
    def unapply(event: HyperlinkEvent) = Option(event.getURL).map(_.getProtocol).collect {
      case "http" => event.getURL
    }
  }

  private object Action {
    def unapply(event: HyperlinkEvent) = Option(event.getURL).map(_.getProtocol).collect {
      case "ftp" => event.getURL.getHost
    }
  }

  private object Widget extends StatusBarWidget {
    def ID = "TypeAwareHighlighting"

    def getPresentation(platformType: PlatformType) = Presentation

    def install(statusBar: StatusBar) {}

    def dispose = {}

    object Presentation extends StatusBarWidget.IconPresentation {
      def getIcon = if(enabled) Icons.TYPED else Icons.UNTYPED

      def getClickConsumer = ClickConsumer

      def getTooltipText = "%s (click to %s, or press Ctrl+Shift+Alt+E)"
              .format(status, if(enabled) "disable" else "enable")

      object ClickConsumer extends Consumer[MouseEvent] {
        def consume(t: MouseEvent) {
          toggle()
        }
      }
    }
  }

  private object FacetListener extends ProjectWideFacetAdapter[ScalaFacet]() {
    override def facetAdded(facet: ScalaFacet) {
      configureWidget()
      notifyIfNeeded()
    }

    override def facetRemoved(facet: ScalaFacet) {
      configureWidget()
    }
  }
}

object HighlightingAdvisor {
  def getInstance(project: Project) = project.getComponent(classOf[HighlightingAdvisor])
}