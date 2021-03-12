package org.jetbrains.plugins.scala.externalHighlighters.compiler

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.ExtensionPointDeclaration

@ApiStatus.Internal
trait WorksheetCompiler {

  def compile(psiFile: PsiFile, document: Document, client: Client): Unit
}

object WorksheetCompiler
  extends ExtensionPointDeclaration[WorksheetCompiler]("org.intellij.scala.worksheetCompiler")
    with WorksheetCompiler {

  override def compile(psiFile: PsiFile, document: Document, client: Client): Unit =
    implementations.foreach(_.compile(psiFile, document, client))
}
