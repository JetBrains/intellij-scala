package org.jetbrains.plugins.scala
package compiler

import com.intellij.openapi.project.{DumbAware, Project}
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
import java.awt.Point
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.{Separator, AnActionEvent, AnAction, DefaultActionGroup}
import com.intellij.compiler.CompilerWorkspaceConfiguration
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages

/**
 * @author Pavel Fatin
 */
class CompileServerManager(project: Project) extends ProjectComponent {
   private val IconRunning = Icons.COMPILE_SERVER

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
         removeWidget()
       }
       case (false, false) => // do nothing
     }
   }

  def removeWidget() {
    if (installed) {
      bar.removeWidget(Widget.ID)
      installed = false
    }
  }

  private def updateWidget() {
     bar.updateWidget(Widget.ID)
   }

   private def applicable = running ||
           CompilerWorkspaceConfiguration.getInstance(project).USE_COMPILE_SERVER &&
                   ScalaApplicationSettings.getInstance.COMPILE_SERVER_ENABLED &&
                   ScalaFacet.isPresentIn(project)

   private def running = launcher.running

   private var installed = false

   private def launcher = CompileServerLauncher.instance

   private def bar = WindowManager.getInstance.getStatusBar(project)

   private def registry: ProjectWideFacetListenersRegistry =
     ProjectWideFacetListenersRegistry.getInstance(project)


   private object Widget extends StatusBarWidget {
     def ID = "Compile server"

     def getPresentation(platformType : PlatformType) = Presentation

     def install(statusBar: StatusBar) {}

     def dispose() {}

     object Presentation extends StatusBarWidget.IconPresentation {
       def getIcon = if(running) IconRunning else IconStopped

       def getClickConsumer = ClickConsumer

       def getTooltipText = title + launcher.port.map(_.formatted(" (TCP %d)")).getOrElse("")

       object ClickConsumer extends Consumer[MouseEvent] {
         def consume(t: MouseEvent) {
           toggleList(t)
         }
       }
     }
   }

   private def title = "Scala compile server"

   private def toggleList(e: MouseEvent) {
     val mnemonics = JBPopupFactory.ActionSelectionAid.MNEMONICS
     val group = new DefaultActionGroup(Start, Stop, Separator.getInstance, Configure)
     val context = DataManager.getInstance.getDataContext(e.getComponent)
     val popup = JBPopupFactory.getInstance.createActionGroupPopup(title, group, context, mnemonics, true)
     val dimension = popup.getContent.getPreferredSize
     val at = new Point(0, -dimension.height)
     popup.show(new RelativePoint(e.getComponent, at))
   }

   private object Start extends AnAction("&Run", "Start compile server", IconLoader.getIcon("/actions/execute.png")) with DumbAware {
     override def update(e: AnActionEvent) {
       e.getPresentation.setEnabled(!launcher.running)
     }

     def actionPerformed(e: AnActionEvent) {
       val sdk = ProjectRootManager.getInstance(project).getProjectSdk

       if (sdk != null) {
         launcher.init(sdk)
       } else {
         Messages.showErrorDialog("No project SDK to run Scala compile server.\n" +
                 "Please either disable Scala compile server or specify a project SDK",
           "No project SDK to run Scala compile server")
       }
     }
   }

   private object Stop extends AnAction("&Stop", "Shutdown compile server", IconLoader.getIcon("/actions/suspend.png")) with DumbAware {
     override def update(e: AnActionEvent) {
       e.getPresentation.setEnabled(launcher.running)
     }

     def actionPerformed(e: AnActionEvent) {
       launcher.stop()
     }
   }

  private object Configure extends AnAction("&Configure...", "Configure compile server", IconLoader.getIcon("/general/configure.png")) with DumbAware {
    def actionPerformed(e: AnActionEvent) {
      ShowSettingsUtil.getInstance().showSettingsDialog(null, "Scala")
    }
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
           val message = "Started" + launcher.port.map(_.formatted(" on TCP %d")).getOrElse("") + "."
           Notifications.Bus.notify(new Notification("scala", title, message, NotificationType.INFORMATION), project)
         case (true, false) =>
           Notifications.Bus.notify(new Notification("scala", title, "Stopped.", NotificationType.INFORMATION), project)
         case _ =>
       }

       wasRunning = nowRunning

       val errors = launcher.errors()

       if (errors.nonEmpty) {
         Notifications.Bus.notify(new Notification("scala", title, errors.mkString, NotificationType.ERROR), project)
       }
     }
   }
 }

object CompileServerManager {
  def instance(project: Project) = project.getComponent(classOf[CompileServerManager])
}