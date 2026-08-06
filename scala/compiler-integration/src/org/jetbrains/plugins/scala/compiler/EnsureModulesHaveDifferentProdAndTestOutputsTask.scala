package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{CompilerModuleExtension, ModuleRootManager}
import com.intellij.openapi.ui.Messages
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.util.compile.ScalaCompileTask

/**
 * Checks whether all modules have different output directories for production files.
 * Shows a notification suggesting to fix it if output directories are the same.
 */
final class EnsureModulesHaveDifferentProdAndTestOutputsTask extends ScalaCompileTask {

  override protected def run(context: CompileContext): Boolean = {
    val project = context.getProject

    if (!project.hasScala) {
      return true
    }

    checkCompilationSettings(project)
  }

  override protected def presentableName: String = "Ensuring modules have different production and test output paths"

  override protected def log: Logger = EnsureModulesHaveDifferentProdAndTestOutputsTask.Log

  private def checkCompilationSettings(project: Project): Boolean = {
    def hasClashes(module: Module) = module.hasScala && {
      val extension = CompilerModuleExtension.getInstance(module)
      val production = extension.getCompilerOutputUrl
      val test = extension.getCompilerOutputUrlForTests
      production == test
    }

    def maySplitSilently(module: Module): Boolean = {
      val rootManager = ModuleRootManager.getInstance(module)
      val sourceRoots = rootManager.getSourceRoots(JavaSourceRootType.SOURCE)
      val testRoots = rootManager.getSourceRoots(JavaSourceRootType.TEST_SOURCE)
      sourceRoots.isEmpty || testRoots.isEmpty
    }

    def splitOutputs(module: Module): Unit = {
      val model = module.modifiableModel
      val extension = model.getModuleExtension(classOf[CompilerModuleExtension])

      val outputUrlParts = extension.getCompilerOutputUrl match {
        case null => Seq.empty
        case url => url.split("/").toSeq
      }
      val nameForTests = if (outputUrlParts.lastOption.contains("classes")) "test-classes" else "test"

      extension.inheritCompilerOutputPath(false)
      extension.setCompilerOutputPathForTests((outputUrlParts.dropRight(1) :+ nameForTests).mkString("/"))

      model.commit()
    }

    def showSplitDialog(modulesWithClashes: Seq[Module]) = {
      Messages.showYesNoDialog(project,
        CompilerIntegrationBundle.message("production.and.test.output.paths.are.shared.in", modulesWithClashes.map(_.getName).mkString(" ")),
        CompilerIntegrationBundle.message("shared.compile.output.paths.in.scala.modules"),
        CompilerIntegrationBundle.message("split.output.paths.automatically"),
        CompilerIntegrationBundle.message("cancel.compilation"),
        Messages.getErrorIcon
      )
    }

    val modulesWithClashes = ModuleManager.getInstance(project).getModules.toSeq.filter(hasClashes)

    var mayProceedWithCompilation = true

    if (modulesWithClashes.nonEmpty) {
      val splitSilently = ApplicationManager.getApplication.isUnitTestMode ||
        modulesWithClashes.forall(maySplitSilently)

      invokeAndWait {
        val splitAutomatically = splitSilently || showSplitDialog(modulesWithClashes) == Messages.YES

        if (splitAutomatically) {
          inWriteAction {
            modulesWithClashes.foreach(splitOutputs)
          }
          project.save()
        }

        mayProceedWithCompilation = splitAutomatically
      }
    }

    mayProceedWithCompilation
  }
}
private object EnsureModulesHaveDifferentProdAndTestOutputsTask {
  private val Log: Logger = Logger.getInstance(classOf[EnsureModulesHaveDifferentProdAndTestOutputsTask])
}
