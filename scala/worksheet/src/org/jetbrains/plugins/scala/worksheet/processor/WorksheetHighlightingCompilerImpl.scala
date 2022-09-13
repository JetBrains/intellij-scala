package org.jetbrains.plugins.scala.worksheet.processor

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiFile
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import scala.concurrent.duration.DurationInt

final class WorksheetHighlightingCompilerImpl
  extends org.jetbrains.plugins.scala.compiler.highlighting.WorksheetHighlightingCompiler {

  override def compile(file: ScalaFile, document: Document, client: Client): Unit = {
    if (!file.isWorksheetFile)
      return

    if (file.getProject.isDisposed)
      return

    val module = file.module.getOrElse(return)
    val compiler = new WorksheetCompiler(module, file)
    compiler.compileOnlySync(
      document,
      client,
      waitAtMost = 60.seconds
    )
  }
}
