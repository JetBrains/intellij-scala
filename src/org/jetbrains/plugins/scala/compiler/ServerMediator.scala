package org.jetbrains.plugins.scala
package compiler

import java.util.UUID

import com.intellij.compiler.server.BuildManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.{CompileContext, CompileTask, CompilerManager}
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{CompilerModuleExtension, ModuleRootManager}
import com.intellij.openapi.ui.Messages
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project._

/**
 * Pavel Fatin
 */

class ServerMediator(project: Project) extends ProjectComponent {

  private def isScalaProject = project.hasScala
  private val settings = ScalaCompileServerSettings.getInstance

  private val connection = project.getMessageBus.connect
  private val serverLauncher = new BuildManagerListener {
    override def beforeBuildProcessStarted(project: Project, uuid: UUID): Unit = {}

    override def buildStarted(project: Project, sessionId: UUID, isAutomake: Boolean): Unit = {
      if (settings.COMPILE_SERVER_ENABLED && isScalaProject && !ApplicationManager.getApplication.isUnitTestMode) {
        invokeAndWait {
          CompileServerManager.instance(project).configureWidget()
        }

        if (CompileServerLauncher.needRestart(project)) {
          CompileServerLauncher.instance.stop()
        }

        if (!CompileServerLauncher.instance.running) {
          invokeAndWait {
            CompileServerLauncher.instance.tryToStart(project)
          }
        }
      }
    }

    override def buildFinished(project: Project, sessionId: UUID, isAutomake: Boolean): Unit = {}
  }

  connection.subscribe(BuildManagerListener.TOPIC, serverLauncher)

  private val checkSettingsTask = new CompileTask {
    def execute(context: CompileContext): Boolean = {
      if (isScalaProject) {
        if (!checkCompilationSettings()) false
        else true
      }
      else true
    }
  }

  CompilerManager.getInstance(project).addBeforeTask(checkSettingsTask)

  private def checkCompilationSettings(): Boolean = {
    def hasClashes(module: Module) = module.hasScala && {
      val extension = CompilerModuleExtension.getInstance(module)
      val production = extension.getCompilerOutputUrl
      val test = extension.getCompilerOutputUrlForTests
      production == test
    }
    val modulesWithClashes = ModuleManager.getInstance(project).getModules.toSeq.filter(hasClashes)

    var result = true

    if (modulesWithClashes.nonEmpty) {
      invokeAndWait {
        val choice =
          if (!ApplicationManager.getApplication.isUnitTestMode) {
            Messages.showYesNoDialog(project,
              "Production and test output paths are shared in: " + modulesWithClashes.map(_.getName).mkString(" "),
              "Shared compile output paths in Scala module(s)",
              "Split output path(s) automatically", "Cancel compilation", Messages.getErrorIcon)
          }
          else Messages.YES

        val splitAutomatically = choice == Messages.YES

        if (splitAutomatically) {
          inWriteAction {
            modulesWithClashes.foreach { module =>
              val model = ModuleRootManager.getInstance(module).getModifiableModel
              val extension = model.getModuleExtension(classOf[CompilerModuleExtension])

              val outputUrlParts = extension.getCompilerOutputUrl match {
                case null => Seq.empty
                case url => url.split("/").toSeq
              }
              val nameForTests = if (outputUrlParts.last == "classes") "test-classes" else "test"

              extension.inheritCompilerOutputPath(false)
              extension.setCompilerOutputPathForTests((outputUrlParts.dropRight(1) :+ nameForTests).mkString("/"))

              model.commit()
            }

            project.save()
          }
        }

        result = splitAutomatically
      }
    }

    result
  }

  def getComponentName = getClass.getSimpleName

  def initComponent() {}

  def disposeComponent() {}

  def projectOpened() {}

  def projectClosed() {}
}
