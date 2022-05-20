package org.jetbrains.plugins.scala.findUsages.compilerReferences.search

import com.intellij.openapi.editor.Document
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile, PsiManager}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.UsagesInFile

object UsageToPsiElements {
  private[search] final case class ElementsInContext(
    elements: Seq[(PsiElement, Int)],
    file:     PsiFile,
    doc:      Document,
  )

  private[search] def extractCandidatesFromUsage(
    psiManager:    PsiManager,
    psiDocManager: PsiDocumentManager,
    usage:         UsagesInFile
  ): Option[ElementsInContext] =
    for {
      psiFile    <- psiManager.findFile(usage.file).toOption
      document   <- psiDocManager.getDocument(psiFile).toOption
      lineNumber = (e: PsiElement) => document.getLineNumber(e.getTextOffset) + 1
      canBeUsage = usage.lines.contains _
    } yield {
      val elems = psiFile.depthFirst().flatMap { e =>
        val line = lineNumber(e)
        canBeUsage(line).option(e -> line)
      }.toList

      ElementsInContext(elems, psiFile, document)
    }
}
