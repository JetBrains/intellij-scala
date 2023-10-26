package org.jetbrains.plugins.scala.compiler.references

import com.intellij.compiler.server.BuildProcessParametersProvider
import com.intellij.openapi.project.Project
import org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.compiler.references.indices.ScalaCompilerIndices
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration

class CompilerIndicesBuildProcessParametersProvider(project: Project) extends BuildProcessParametersProvider {
  override def getVMArguments: java.util.List[String] = {
    if (project.hasScala) {
      val args = new java.util.ArrayList[String]()
      if (!upToDateCompilerIndexExists(project, ScalaCompilerIndices.version)) {
        args.add(s"-D${Builder.rebuildPropertyKey}=true")
      }
      if (ScalaCompilerConfiguration(project).incrementalityType == IncrementalityType.SBT) {
        args.add(s"-D${JavaBackwardReferenceIndexWriter.PROP_KEY}=false")
      }
      args
    } else {
      java.util.Collections.emptyList()
    }
  }
}
