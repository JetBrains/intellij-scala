package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.ExtensionPointDeclaration

// NOTE: this interface was created when parallel work was done by me (Dmitrii Naumenko) during Worksheet Migration
// and Artyom Semyonov during other work on JPS. We probably could revise it interfaces and unify
// HighlightingCompilerHelper with HighlightingCompiler
@ApiStatus.Internal
trait HighlightingCompilerHelper {
  def canHighlight(file: PsiFile): Boolean
  def runHighlightingCompilation(project: Project, file: PsiFile, document: Document, client: Client): Unit
}

object HighlightingCompilerHelper
  extends ExtensionPointDeclaration[HighlightingCompilerHelper](
    "org.intellij.scala.highlightingCompilerHelper"
  )