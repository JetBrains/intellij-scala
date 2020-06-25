package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.util
import java.util.Collections

import com.intellij.compiler.server.BuildProcessParametersProvider
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compilerReferences.Builder
import org.jetbrains.plugins.scala.findUsages.compilerReferences.indices.ScalaCompilerIndices

class CompilerIndicesBuildProcessParametersProvider(project: Project) extends BuildProcessParametersProvider {
  override def getVMArguments: util.List[String] =
    if (upToDateCompilerIndexExists(project, ScalaCompilerIndices.version))
      Collections.emptyList()
    else Collections.singletonList(s"""-D${Builder.rebuildPropertyKey}=true""")
}
