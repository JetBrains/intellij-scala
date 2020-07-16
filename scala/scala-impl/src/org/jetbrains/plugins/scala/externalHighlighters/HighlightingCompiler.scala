package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.ExtensionPointDeclaration

@ApiStatus.Internal()
trait HighlightingCompiler {
  def canHighlight(file: PsiFile): Boolean
  def runHighlightingCompilation(project: Project, file: PsiFile, document: Document, client: Client): Unit
}

object HighlightingCompiler
  extends ExtensionPointDeclaration[HighlightingCompiler](
    "org.intellij.scala.highlightingCompiler"
  )
