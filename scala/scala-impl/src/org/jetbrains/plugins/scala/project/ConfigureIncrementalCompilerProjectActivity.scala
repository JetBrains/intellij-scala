package org.jetbrains.plugins.scala.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.{ModalityState, ReadAction}
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.{FileTypeRegistry, UnknownFileType}
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.{FileTypeIndex, GlobalSearchScope}
import com.intellij.util.Processor
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.startup.ProjectActivity

import java.util.concurrent.{Callable, ExecutorService}
import java.util.function.Consumer

private final class ConfigureIncrementalCompilerProjectActivity extends ProjectActivity {

  import ConfigureIncrementalCompilerProjectActivity._

  override def execute(project: Project): Unit = {
    project.subscribeToModuleRootChanged() { event =>
      if (event.isCausedByWorkspaceModelChangesOnly && !project.isDisposed && project.hasScala) {
        val fileType = FileTypeRegistry.getInstance().getFileTypeByExtension("kt")
        fileType match {
          case UnknownFileType.INSTANCE =>
          // The Kotlin plugin is not enabled. Kotlin code cannot be compiled in this case, so it is ok to use Zinc.
          case kotlin =>
            // `true` means that there are Kotlin sources in the project
            val callable: Callable[Boolean] = { () =>
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
                  !FileTypeIndex.processFiles(kotlin, processor, moduleScope)
                }
              }
            }

            val consumer: Consumer[Boolean] = { hasKotlin =>
              if (hasKotlin && !project.isDisposed) {
                // There are Kotlin source files in the project.
                ScalaCompilerConfiguration.instanceIn(project).incrementalityType = IncrementalityType.IDEA
              }
            }

            val executor = backgroundExecutor(project)
            ReadAction
              .nonBlocking(callable)
              .expireWhen(() => project.isDisposed)
              .coalesceBy(EqualityToken(project))
              .finishOnUiThread(ModalityState.defaultModalityState(), consumer)
              .submit(executor)
        }
      }
    }
  }
}

private object ConfigureIncrementalCompilerProjectActivity {

  private val Log: Logger = Logger.getInstance(classOf[ConfigureIncrementalCompilerProjectActivity])

  @Service(Array(Service.Level.PROJECT))
  private final class BackgroundService extends Disposable {
    val executor: ExecutorService =
      AppExecutorUtil.createBoundedScheduledExecutorService("ConfigureIncrementalCompiler background executor", 1)

    override def dispose(): Unit = {
      executor.shutdown()
    }
  }

  private def backgroundExecutor(project: Project): ExecutorService =
    project.getService(classOf[BackgroundService]).executor

  private final case class EqualityToken(project: Project)
}
