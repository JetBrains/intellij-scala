package org.jetbrains.plugins.scala.worksheet.processor

import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.Module
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import scala.concurrent.duration.DurationInt

final class WorksheetHighlightingCompilerImpl
  extends org.jetbrains.plugins.scala.compiler.highlighting.WorksheetHighlightingCompiler {

  override def compile(
    psiFile: ScalaFile,
    document: Document,
    module: Module,
    client: Client,
  ): Unit = {
    assert(psiFile.isWorksheetFile, "WorksheetHighlightingCompilerImpl must be called for worksheet files only")

    if (module.isDisposed)
      return

    val compiler = new WorksheetCompiler(module, psiFile)
    compiler.compileOnlySync(
      document,
      client,
      waitAtMost = 60.seconds
    )
  }
}
