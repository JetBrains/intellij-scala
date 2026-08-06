package org.jetbrains.sbt.project.modifier.location

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.modifier.BuildFileElementType

import java.nio.file.Path

object SimpleModuleBuildFileProvider extends BuildFileProvider {

  override def findIoFile(module: Module, elementType: BuildFileElementType): Option[BuildFileEntry[Path]] = {
    val buildFile = Path.of(module.getModuleFilePath).getParent / Sbt.BuildFile
    if (buildFile.exists) Some(BuildFileEntry(buildFile, isModuleLocal = true)) else None
  }
}
