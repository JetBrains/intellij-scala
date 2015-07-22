package org.jetbrains.plugins.scala.meta

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

trait EnvironmentProvider {
  def findFileByPath(path: String): PsiFile
  def getCurrentProject: Project
}

