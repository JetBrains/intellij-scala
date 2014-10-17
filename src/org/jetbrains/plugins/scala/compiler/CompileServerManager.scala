package org.jetbrains.plugins.scala
package compiler

import java.awt.Point
import java.awt.event.{ActionEvent, ActionListener, MouseEvent}
import javax.swing.Timer

import com.intellij.facet.{ProjectWideFacetAdapter, ProjectWideFacetListenersRegistry}
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.ide.ui.search.SearchUtil
import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, DefaultActionGroup, Separator}
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.StatusBarWidget.PlatformType
import com.intellij.openapi.wm.{StatusBar, StatusBarWidget, WindowManager}
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.config.ScalaFacet
import org.jetbrains.plugins.scala.icons.Icons

import scala.collection.JavaConverters._

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

   private object Start extends AnAction("&Run", "Start compile server", AllIcons.Actions.Execute) with DumbAware {
     override def update(e: AnActionEvent) {
       e.getPresentation.setEnabled(!launcher.running)
     }

     def actionPerformed(e: AnActionEvent) {
       launcher.tryToStart(project)
     }
   }

   private object Stop extends AnAction("&Stop", "Shutdown compile server", AllIcons.Actions.Suspend) with DumbAware {
     override def update(e: AnActionEvent) {
       e.getPresentation.setEnabled(launcher.running)
     }

     def actionPerformed(e: AnActionEvent) {
       launcher.stop(e.getProject)
     }
   }

  private object Configure extends AnAction("&Configure...", "Configure compile server", AllIcons.General.Settings) with DumbAware {
    def actionPerformed(e: AnActionEvent) {
      val groups = ShowSettingsUtilImpl.getConfigurableGroups(project, true)
      val all = SearchUtil.expand(groups)
      val configurable = all.asScala.find(_.isInstanceOf[ScalaApplicationSettingsForm]).getOrElse {
        throw new Exception("Could not find settings dialog for compile server")
      }
      ShowSettingsUtilImpl.getDialog(project, groups, configurable).show()
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
     private var wasRunning: Option[Boolean] = None

     def actionPerformed(e: ActionEvent) {
       val nowRunning = running

       if (installed || nowRunning) updateWidget()

       wasRunning -> nowRunning match {
         case (Some(false), true) =>
//           val message = "Started" + launcher.port.map(_.formatted(" on TCP %d")).getOrElse("") + "."
//           Notifications.Bus.notify(new Notification("scala", title, message, NotificationType.INFORMATION), project)
         case (Some(true), false) =>
//           Notifications.Bus.notify(new Notification("scala", title, "Stopped.", NotificationType.INFORMATION), project)
         case _ =>
       }

       wasRunning = Some(nowRunning)

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