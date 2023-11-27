package org.jetbrains.plugins.scala.project

import com.intellij.openapi.fileTypes.{FileTypeRegistry, UnknownFileType}
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.{FileTypeIndex, GlobalSearchScope}
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.startup.ProjectActivity

private final class ConfigureIncrementalCompilerProjectActivity extends ProjectActivity {

  override def execute(project: Project): Unit = {
    project.subscribeToModuleRootChanged() { event =>
      if (event.isCausedByWorkspaceModelChangesOnly && !project.isDisposed && project.hasScala) {
        val fileType = FileTypeRegistry.getInstance().getFileTypeByExtension("kt")
        fileType match {
          case UnknownFileType.INSTANCE =>
          // The Kotlin plugin is not enabled. Kotlin code cannot be compiled in this case, so it is ok to use Zinc.
          case kotlin =>
            val modules = ModuleManager.getInstance(project).getModules
            val hasKotlin = modules.filterNot(_.hasBuildModuleType).exists { module =>
              val moduleScope = GlobalSearchScope.moduleScope(module)
              !FileTypeIndex.processFiles(kotlin, _ => false, moduleScope)
            }
            if (hasKotlin) {
              ScalaCompilerConfiguration.instanceIn(project).incrementalityType = IncrementalityType.IDEA
            }
        }
      }
    }
  }
}
