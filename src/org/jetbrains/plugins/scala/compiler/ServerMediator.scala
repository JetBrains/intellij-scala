package org.jetbrains.plugins.scala
package compiler

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

  CompilerManager.getInstance(project).addBeforeTask(new CompileTask {

    def execute(context: CompileContext): Boolean = {

      val externalCompiler = true // TODO In-process build is now deprecated

      if (isScalaProject) {

        if (externalCompiler) {
          if (!checkCompilationSettings()) {
            return false
          }

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
    def hasClashes(module: Module) = module.hasScala && {
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
