package org.jetbrains.plugins.scala
package compiler

import com.intellij.openapi.project.Project
import com.intellij.openapi.components.ProjectComponent
import config.ScalaFacet
import com.intellij.compiler.CompilerWorkspaceConfiguration
import com.intellij.openapi.compiler.{CompileContext, CompileTask, CompilerManager}
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.roots.{ModuleRootManager, CompilerModuleExtension}
import com.intellij.openapi.ui.Messages
import extensions._
import com.intellij.openapi.application.ApplicationManager
import com.intellij.internal.statistic.UsageTrigger
import org.jetbrains.plugin.scala.compiler.IncrementalType


/**
 * Pavel Fatin
 */

class ServerMediator(project: Project) extends ProjectComponent {

  CompilerManager.getInstance(project).addBeforeTask(new CompileTask {
    var firstCompilation = true

    def execute(context: CompileContext): Boolean = {
      val scalaProject = ScalaFacet.isPresentIn(project)

      val externalCompiler = CompilerWorkspaceConfiguration.getInstance(project).USE_OUT_OF_PROCESS_BUILD

      def collectStats() {
        val withCompileServerId = "scala.compilation.with.server"
        val withoutCompileServerId = "scala.compilation.without.server"
        val incrementalByIdeaId = "scala.compilation.incremental.by.idea"
        val incrementalBySbtId = "scala.compilation.incremental.by.sbt"

        if (externalCompiler) UsageTrigger.trigger(withCompileServerId)
        else UsageTrigger.trigger(withoutCompileServerId)

        ScalaApplicationSettings.getInstance().INCREMENTAL_TYPE match {
          case IncrementalType.SBT => UsageTrigger.trigger(incrementalBySbtId)
          case IncrementalType.IDEA => UsageTrigger.trigger(incrementalByIdeaId)
        }
      }

      if (scalaProject) {
        collectStats()

        if (externalCompiler) {
          invokeAndWait {
            if (!checkCompilationSettings()) {
              return false
            }
            project.getComponent(classOf[FscServerLauncher]).stop()
            project.getComponent(classOf[FscServerManager]).removeWidget()
          }

          val settings = ScalaApplicationSettings.getInstance

          if (settings.COMPILE_SERVER_ENABLED && !ApplicationManager.getApplication.isUnitTestMode) {
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
            CompileServerLauncher.instance.stop(project)
            CompileServerManager.instance(project).removeWidget()
          }
        }
      }

      true
    }
  })

  private def checkCompilationSettings(): Boolean = {
    def hasClashes(module: Module) = ScalaFacet.findIn(module).isDefined && {
      val extension = CompilerModuleExtension.getInstance(module)
      val production = extension.getCompilerOutputUrl
      val test = extension.getCompilerOutputUrlForTests
      production == test
    }
    val modulesWithClashes = ModuleManager.getInstance(project).getModules.toSeq.filter(hasClashes)

    if (modulesWithClashes.nonEmpty) {
      val result = Messages.showYesNoDialog(project,
        "Production and test output paths are shared in: " + modulesWithClashes.map(_.getName).mkString(" "),
        "Shared compile output paths in Scala module(s)",
        "Split output path(s) automatically", "Cancel compilation", Messages.getErrorIcon)

      val splitAutomatically = result == Messages.YES

      if (splitAutomatically) {
        inWriteAction {
          modulesWithClashes.foreach { module =>
            val model = ModuleRootManager.getInstance(module).getModifiableModel
            val extension = model.getModuleExtension(classOf[CompilerModuleExtension])

            val name = if (extension.getCompilerOutputPath.getName == "classes") "test-classes" else "test"

            extension.inheritCompilerOutputPath(false)
            extension.setCompilerOutputPathForTests(extension.getCompilerOutputPath.getParent.getUrl + "/" + name)

            model.commit()
          }

          project.save()
        }
      }

      splitAutomatically
    } else {
      true
    }
  }

  def getComponentName = getClass.getSimpleName

  def initComponent() {}

  def disposeComponent() {}

  def projectOpened() {}

  def projectClosed() {}
}
