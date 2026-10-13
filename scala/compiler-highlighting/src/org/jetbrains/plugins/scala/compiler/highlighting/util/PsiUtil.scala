package org.jetbrains.plugins.scala.compiler.highlighting.util

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

object PsiUtil:
  def isWorksheet(psi: PsiFile): Boolean = psi match
    case scalaFile: ScalaFile => scalaFile.isWorksheetFile
    case _ => false