package org.jetbrains.plugins.scala.meta

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.{PsiManager, PsiFile}
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

trait FileProvider {
  def findFileByPath(path: String): PsiFile
}

