package org.jetbrains.plugins.scala
package compiler

import java.awt.Point
import java.awt.event.{ActionEvent, ActionListener, MouseEvent}
import javax.swing.{Icon, Timer}

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, DefaultActionGroup, Separator}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.StatusBarWidget.PlatformType
import com.intellij.openapi.wm.{StatusBar, StatusBarWidget, WindowManager}
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import org.jetbrains.plugins.scala.compiler.CompileServerManager._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.project._

/**
 * @author Pavel Fatin
 */
class CompileServerManager(project: Project) extends AbstractProjectComponent(project) {
   private val IconRunning = Icons.COMPILE_SERVER

   private val IconStopped = IconLoader.getDisabledIcon(IconRunning)

   private val timer = new Timer(1000, TimerListener)

   override def projectOpened() {
     if (ApplicationManager.getApplication.isUnitTestMode) return

     project.scalaEvents.addScalaProjectListener(ScalaListener)
     configureWidget()
     timer.setRepeats(true)
     timer.start()
   }

   override def projectClosed() {
     if (ApplicationManager.getApplication.isUnitTestMode) return

     project.scalaEvents.removeScalaProjectListener(ScalaListener)
     configureWidget()
     timer.stop()
   }

   override def getComponentName: String = getClass.getSimpleName

   def configureWidget() {
     if (ApplicationManager.getApplication.isUnitTestMode) return

     (applicable, installed) match {
       case (true, true) => // do nothing
       case (true, false) =>
         bar.foreach { b =>
           b.addWidget(Widget, "before Position", project)
           installed = true
         }
       case (false, true) =>
         removeWidget()
       case (false, false) => // do nothing
     }
   }

  def removeWidget() {
    if (installed) {
      bar.foreach(_.removeWidget(Widget.ID))
      installed = false
    }
  }

  private def updateWidget() {
    bar.foreach(_.updateWidget(Widget.ID))
  }

  private def applicable = running ||
          ScalaCompileServerSettings.getInstance.COMPILE_SERVER_ENABLED &&
                  project.hasScala

   private def running = launcher.running

   private var installed = false

   private def launcher = CompileServerLauncher

   private def bar = Option(WindowManager.getInstance.getStatusBar(project))

   private object Widget extends StatusBarWidget {
     def ID = "Compile server"

     def getPresentation(platformType : PlatformType) = Presentation

     def install(statusBar: StatusBar) {}

     def dispose() {}

     object Presentation extends StatusBarWidget.IconPresentation {
       def getIcon: Icon = if(running) IconRunning else IconStopped

       def getClickConsumer = ClickConsumer

       def getTooltipText: String = title + launcher.port.map(_.formatted(" (TCP %d)")).getOrElse("")

       object ClickConsumer extends Consumer[MouseEvent] {
         def consume(t: MouseEvent) {
           toggleList(t)
         }
       }
     }
   }

   private def title = "Scala Compile Server"

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
      showCompileServerSettingsDialog(project)
    }
  }

  private object ScalaListener extends ScalaProjectListener {
    def onScalaProjectChanged() {
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
         Notifications.Bus.notify(new Notification(title, title, errors.mkString, NotificationType.ERROR), project)
       }
     }
   }
 }

object CompileServerManager {

  def configureWidget(project: Project): Unit = {
    if (!project.isDisposed) {
      val instance = project.getComponent(classOf[CompileServerManager])
      instance.configureWidget()
    }
  }

  def showCompileServerSettingsDialog(project: Project): Unit = {
    ShowSettingsUtil.getInstance().showSettingsDialog(project, "Scala Compile Server")
  }
}