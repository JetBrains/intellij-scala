package org.jetbrains.plugins.scala.compiler.references

import com.intellij.compiler.server.BuildProcessParametersProvider
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.references.Builder
import org.jetbrains.plugins.scala.compiler.references.indices.ScalaCompilerIndices

import java.util
import java.util.Collections

class CompilerIndicesBuildProcessParametersProvider(project: Project) extends BuildProcessParametersProvider {
  override def getVMArguments: util.List[String] =
    if (upToDateCompilerIndexExists(project, ScalaCompilerIndices.version))
      Collections.emptyList()
    else Collections.singletonList(s"""-D${Builder.rebuildPropertyKey}=true""")
}
