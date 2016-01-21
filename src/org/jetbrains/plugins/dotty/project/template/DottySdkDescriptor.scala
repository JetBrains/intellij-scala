package org.jetbrains.plugins.dotty.project.template

import java.io.File

import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.plugins.scala.project.template.{Artifact, SdkDescriptor, SdkDescriptorCompanion}

/**
  * @author adkozlov
  */
case class DottySdkDescriptor(version: Option[Version],
                              compilerFiles: Seq[File],
                              libraryFiles: Seq[File],
                              sourceFiles: Seq[File],
                              docFiles: Seq[File]) extends SdkDescriptor {
  override protected val LanguageName = "dotty"
}

object DottySdkDescriptor extends SdkDescriptorCompanion {
  override protected val RequiredAdditionalBinaries = Set[Artifact](Artifact.DottyCompiler, Artifact.JLine)

  override protected def createSdkDescriptor = DottySdkDescriptor(_, _, _, _, _)
}
