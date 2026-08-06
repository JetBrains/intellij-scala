package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.Module
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.ExtensionPointDeclaration
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

@ApiStatus.Internal
trait WorksheetHighlightingCompiler {

  def compile(
    psiFile: ScalaFile,
    document: Document,
    module: Module,
    client: Client,
  ): Unit
}

object WorksheetHighlightingCompiler
  extends ExtensionPointDeclaration[WorksheetHighlightingCompiler]("org.intellij.scala.worksheetHighlightingCompiler")
    with WorksheetHighlightingCompiler {

  override def compile(
    psiFile: ScalaFile,
    document: Document,
    module: Module,
    client: Client,
  ): Unit =
    implementations.foreach(_.compile(psiFile, document, module, client))
}
