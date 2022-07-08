package org.jetbrains.sbt.project.modifier.location

import org.jetbrains.sbt.project.modifier.BuildFileElementType

import java.io.File

object SimpleModuleBuildFileProvider extends BuildFileProvider {

  override def findIoFile(module: com.intellij.openapi.module.Module, elementType: BuildFileElementType): Option[BuildFileEntry[File]] = {
    import org.jetbrains.sbt._
    val buildFile = module.getModuleFilePath.toFile.getParentFile / Sbt.BuildFile
    if (buildFile.exists) Some(BuildFileEntry(buildFile, isModuleLocal = true)) else None
  }
}
