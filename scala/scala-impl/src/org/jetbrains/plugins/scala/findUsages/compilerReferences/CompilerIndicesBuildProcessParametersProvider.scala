package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.util
import java.util.Collections

import com.intellij.compiler.server.BuildProcessParametersProvider
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.findUsages.compilerReferences.indices.ScalaCompilerIndices
import org.jetbrains.plugin.scala.compilerReferences.{ScalaCompilerReferenceIndexBuilder => Builder}

class CompilerIndicesBuildProcessParametersProvider(project: Project) extends BuildProcessParametersProvider {
  override def getVMArguments: util.List[String] =
    if (upToDateCompilerIndexExists(project, ScalaCompilerIndices.version))
      Collections.emptyList()
    else Collections.singletonList(s"""-D${Builder.propertyKey}=true""")
}
