package org.jetbrains.sbt.project.modifier.location

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.modifier.BuildFileElementType

import java.nio.file.Path

object ProjectRootBuildFileProvider extends BuildFileProvider {
  override def findIoFile(module: Module, elementType: BuildFileElementType): Option[BuildFileEntry[Path]] = {
    val project = module.getProject
    val buildFile = Path.of(project.getBasePath) / Sbt.BuildFile
    if (buildFile.exists) Some(BuildFileEntry(buildFile, isModuleLocal = false)) else None
  }
}
