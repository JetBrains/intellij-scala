package org.jetbrains.plugins.scala
package components

import com.intellij.openapi.project.Project
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.wm.{StatusBar, StatusBarWidget, WindowManager}
import com.intellij.openapi.wm.StatusBarWidget.PlatformType
import config.ScalaFacet
import com.intellij.facet.{ProjectWideFacetListenersRegistry, ProjectWideFacetAdapter}
import java.awt.event.{ActionEvent, ActionListener, MouseEvent}
import javax.swing.Timer
import com.intellij.openapi.util.IconLoader
import com.intellij.util.Consumer
import com.intellij.notification.{NotificationType, NotificationDisplayType, Notifications, Notification}
import icons.Icons
import compiler.ScalacSettings

/**
 * Pavel Fatin
 */

class CompileServerManager(project: Project) extends ProjectComponent {
  private val IconRunning = Icons.FSC

  private val IconStopped = IconLoader.getDisabledIcon(IconRunning)

  private val timer = new Timer(1000, TimerListener)

  def initComponent() {}

  def disposeComponent() {}

  def projectOpened() {
    registry.registerListener(ScalaFacet.Id, FacetListener)
    configureWidget()
    timer.setRepeats(true)
    timer.start()
  }

  def projectClosed() {
    registry.unregisterListener(ScalaFacet.Id, FacetListener)
    configureWidget()
    timer.stop()
  }

  def getComponentName = getClass.getSimpleName

  def configureWidget() {
    (applicable, installed) match {
      case (true, true) => // do nothing
      case (true, false) => {
        bar.addWidget(Widget, "before Position", project)
        installed = true
      }
      case (false, true) => {
        bar.removeWidget(Widget.ID)
        installed = false
      }
      case (false, false) => // do nothing
    }
  }

  private def updateWidget() {
    bar.updateWidget(Widget.ID)
  }

  private def applicable = running ||
          ScalacSettings.getInstance(project).INTERNAL_SERVER && ScalaFacet.findIn(project).exists(_.fsc)

  private def running = launcher.running

  private var installed = false

  private def launcher = project.getComponent(classOf[CompileServerLauncher])

  private def bar = WindowManager.getInstance.getStatusBar(project)

  private def registry: ProjectWideFacetListenersRegistry =
    ProjectWideFacetListenersRegistry.getInstance(project)


  private object Widget extends StatusBarWidget {
    def ID = "FSC"

    def getPresentation(platformType : PlatformType) = Presentation

    def install(statusBar: StatusBar) {}

    def dispose() {}

    object Presentation extends StatusBarWidget.IconPresentation {
      def getIcon = if(running) IconRunning else IconStopped

      def getClickConsumer = ClickConsumer

      def getTooltipText = if (running) "FSC running on port %d".format(launcher.port) else "FSC stopped"

      object ClickConsumer extends Consumer[MouseEvent] {
        def consume(t: MouseEvent) {
          toggleList(t)
        }
      }
    }
  }

  private def toggleList(e: MouseEvent) {
//    val popup = JBPopupFactory.getInstance.createActionGroupPopup("Something")
//    val dimension = popup.getContent.getPreferredSize
//    val at= new Point(0, -dimension.height)
//    popup.show(new RelativePoint(e.getComponent, at))
  }

  private object FacetListener extends ProjectWideFacetAdapter[ScalaFacet]() {
    override def facetAdded(facet: ScalaFacet) {
      configureWidget()
    }

    override def facetRemoved(facet: ScalaFacet) {
      configureWidget()
    }

    override def facetConfigurationChanged(facet: ScalaFacet) {
      configureWidget()
    }
  }

  private object TimerListener extends ActionListener {
    private var wasRunning = false

    def actionPerformed(e: ActionEvent) {
      val nowRunning = running

      if (installed || nowRunning) updateWidget()

      wasRunning -> nowRunning match {
        case (false, true) =>
          val message = "Started on port %d".format(launcher.port)
          val notification = new Notification("scala", "Fast Scala Compiler", message, NotificationType.INFORMATION)
          Notifications.Bus.register("scala", NotificationDisplayType.BALLOON)
          Notifications.Bus.notify(notification, project)
        case (true, false) =>
          val message = "Stopped on port %d".format(launcher.port)
          val notification = new Notification("scala", "Fast Scala Compiler", message, NotificationType.INFORMATION)
          Notifications.Bus.register("scala", NotificationDisplayType.BALLOON)
          Notifications.Bus.notify(notification, project)
        case _ =>
      }

      wasRunning = nowRunning
    }
  }
}