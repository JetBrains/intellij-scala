package org.jetbrains.plugins.scala.project

import com.intellij.openapi.application.{ModalityState, ReadAction}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.{FileType, FileTypeRegistry, UnknownFileType}
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ModuleRootEvent, ModuleRootListener}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.{FileTypeIndex, GlobalSearchScope}
import com.intellij.util.Processor
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration

private final class ConfigureIncrementalCompilerModuleRootListener(project: Project) extends ModuleRootListener {

  import ConfigureIncrementalCompilerModuleRootListener._

  override def rootsChanged(event: ModuleRootEvent): Unit = {
    if (event.isCausedByWorkspaceModelChangesOnly && !project.isDisposed && project.hasScala) {
      val fileType = FileTypeRegistry.getInstance().getFileTypeByExtension("kt")
      fileType match {
        case UnknownFileType.INSTANCE =>
        // The Kotlin plugin is not enabled. Kotlin code cannot be compiled in this case, so it is ok to use Zinc.
        case kotlinFileType =>
          ReadAction
            .nonBlocking[Boolean](() => hasKotlinSourceFiles(project, kotlinFileType))
            .inSmartMode(project)
            .expireWhen(() => project.isDisposed)
            .coalesceBy(EqualityToken(project))
            .finishOnUiThread(ModalityState.defaultModalityState(), configureIncrementalCompiler(project, _))
            .submit(AppExecutorUtil.getAppExecutorService)
      }
    }
  }

  /**
   * Looks for Kotlin source files in the project.
   *
   * @note Must be executed in a read action.
   *
   * @return `true` when at least one Kotlin source file has been found in the project, `false` otherwise.
   */
  @RequiresReadLock
  private def hasKotlinSourceFiles(project: Project, kotlinFileType: FileType): Boolean = {
    if (project.isDisposed) false
    else {
      val modules = ModuleManager.getInstance(project).getModules
      modules.filterNot { module =>
        ProgressManager.checkCanceled()
        module.hasBuildModuleType
      }.exists { module =>
        ProgressManager.checkCanceled()
        val moduleScope = GlobalSearchScope.moduleScope(module)
        val processor: Processor[VirtualFile] = { vf =>
          val debugMessage =
            s"Kotlin source file discovered in module ${module.getName} of project ${project.getName}, file path: ${vf.getCanonicalPath}"
          Log.debug(debugMessage)
          false // Stop processing files
        }
        !FileTypeIndex.processFiles(kotlinFileType, processor, moduleScope)
      }
    }
  }

  private def configureIncrementalCompiler(project: Project, hasKotlin: Boolean): Unit = {
    if (hasKotlin && !project.isDisposed) {
      // There are Kotlin source files in the project.
      ScalaCompilerConfiguration.instanceIn(project).incrementalityType = IncrementalityType.IDEA
    }
  }
}

private object ConfigureIncrementalCompilerModuleRootListener {

  private val Log: Logger = Logger.getInstance(classOf[ConfigureIncrementalCompilerModuleRootListener])

  private final case class EqualityToken(project: Project)
}
