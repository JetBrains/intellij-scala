package org.jetbrains.plugins.dotty.project.template

import org.jetbrains.plugins.scala.project.template.{Artifact, SdkDescriptor}

/**
  * @author adkozlov
  */
object DottySdkDescriptor extends SdkDescriptor {
  override protected val compilerArtifact = Artifact.DottyCompiler
}
