package org.jetbrains.plugins.scala.project

import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.psi.search.{FileTypeIndex, ProjectScope}
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.startup.ProjectActivity

private final class ConfigureIncrementalCompilerProjectActivity extends ProjectActivity {

  override def execute(project: Project): Unit = {
    project.subscribeToModuleRootChanged() { _ =>
      if (!project.isDisposed && project.hasScala) {
        val projectContentScope = ProjectScope.getContentScope(project)
        val kotlinFileType = FileTypeRegistry.getInstance().getFileTypeByExtension("kt")
        val hasKotlin = !FileTypeIndex.processFiles(kotlinFileType, _ => false, projectContentScope)
        if (hasKotlin) {
          ScalaCompilerConfiguration.instanceIn(project).incrementalityType = IncrementalityType.IDEA
        }
      }
    }
  }
}
