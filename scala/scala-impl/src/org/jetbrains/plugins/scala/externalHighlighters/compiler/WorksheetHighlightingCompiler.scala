package org.jetbrains.plugins.scala.externalHighlighters.compiler

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.ExtensionPointDeclaration

@ApiStatus.Internal
trait WorksheetHighlightingCompiler {

  def compile(psiFile: PsiFile, document: Document, client: Client): Unit
}

object WorksheetHighlightingCompiler
  extends ExtensionPointDeclaration[WorksheetHighlightingCompiler]("org.intellij.scala.worksheetCompiler")
    with WorksheetHighlightingCompiler {

  override def compile(psiFile: PsiFile, document: Document, client: Client): Unit =
    implementations.foreach(_.compile(psiFile, document, client))
}
