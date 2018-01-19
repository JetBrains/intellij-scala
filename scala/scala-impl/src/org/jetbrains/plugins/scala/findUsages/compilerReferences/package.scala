package org.jetbrains.plugins.scala.findUsages

import java.io.File

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.project.Project

package object compilerReferences {
  def buildDir(project: Project): Option[File] = Option(BuildManager.getInstance().getProjectSystemDirectory(project))
  def indexDir(project: Project): Option[File] = buildDir(project).map(new File(_, "scala-compiler-references"))
}
