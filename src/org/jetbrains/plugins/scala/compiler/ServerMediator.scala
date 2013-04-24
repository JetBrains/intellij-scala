package org.jetbrains.plugins.scala
package compiler

import com.intellij.openapi.project.Project
import com.intellij.openapi.components.ProjectComponent
import config.ScalaFacet
import com.intellij.compiler.CompilerWorkspaceConfiguration
import com.intellij.notification.{NotificationType, Notification, Notifications}
import com.intellij.openapi.compiler.{CompileContext, CompileTask, CompilerManager}
import org.intellij.lang.annotations.Language
import extensions._

/**
 * Pavel Fatin
 */

class ServerMediator(project: Project) extends ProjectComponent {
  CompilerManager.getInstance(project).addBeforeTask(new CompileTask {
    var firstCompilation = true

    def execute(context: CompileContext): Boolean = {
      val scalaProject = ScalaFacet.isPresentIn(project)

      val externalCompiler = CompilerWorkspaceConfiguration.getInstance(project).USE_COMPILE_SERVER

      if (scalaProject) {
        if (externalCompiler) {
          if (firstCompilation) {
            val title = "Using a new Scala compilation subsystem."
            @Language("HTML")
            val message = "<html><body>In case of any compilation problems you may revert to the previous system by clearing:" +
                    "<br>Project&nbsp;Settings&nbsp;|&nbsp;Compiler&nbsp;|&nbsp;Use&nbsp;external&nbsp;build" +
                    "<br><a href='http://blog.jetbrains.com/scala/2012/12/28/a-new-way-to-compile/'>More info...</a></body></html></body></html>"
            Notifications.Bus.notify(new Notification("scala", title, message, NotificationType.INFORMATION, DesktopUtils.LinkHandler))
            firstCompilation = false
          }

          invokeAndWait {
            project.getComponent(classOf[FscServerLauncher]).stop()
            project.getComponent(classOf[FscServerManager]).removeWidget()
          }

          val applicationSettings = ScalaApplicationSettings.getInstance

          if (applicationSettings.COMPILE_SERVER_ENABLED) {
            invokeAndWait {
              CompileServerManager.instance(project).configureWidget()
            }

            if (!CompileServerLauncher.instance.running) {
              var started = false

              invokeAndWait {
                started = CompileServerLauncher.instance.tryToStart(project)
              }

              if (!started) {
                return false
              }
            }
          }
        } else {
          invokeAndWait {
            CompileServerLauncher.instance.stop()
            CompileServerManager.instance(project).removeWidget()
          }
        }
      }

      true
    }
  })

  def getComponentName = getClass.getSimpleName

  def initComponent() {}

  def disposeComponent() {}

  def projectOpened() {}

  def projectClosed() {}
}