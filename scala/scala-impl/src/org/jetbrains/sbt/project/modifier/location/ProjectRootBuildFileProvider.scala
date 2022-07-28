package org.jetbrains.sbt.project.modifier.location

import com.intellij.openapi.module.{Module => IJModule}
import org.jetbrains.sbt.project.modifier.BuildFileElementType

import java.io.File

object ProjectRootBuildFileProvider extends BuildFileProvider {
  override def findIoFile(module: IJModule, elementType: BuildFileElementType): Option[BuildFileEntry[File]] = {
    import org.jetbrains.sbt._
    val project = module.getProject
    val buildFile = project.getBasePath.toFile / Sbt.BuildFile
    if (buildFile.exists) Some(BuildFileEntry(buildFile, isModuleLocal = false)) else None
  }
}
