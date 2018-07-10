package org.jetbrains.plugins.scala.findUsages.compilerReferences

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile, PsiManager}
import org.jetbrains.plugins.scala.extensions._

trait UsageToPsiElements {
  protected final case class ElementsInContext(
    elements: Seq[PsiElement],
    file:     PsiFile,
    doc:      Document
  )

  def extractCandidatesFromUsage(project: Project, usage: UsagesInFile): Option[ElementsInContext] =
    for {
      psiFile    <- PsiManager.getInstance(project).findFile(usage.file).toOption
      document   <- PsiDocumentManager.getInstance(project).getDocument(psiFile).toOption
      lineNumber = (e: PsiElement) => document.getLineNumber(e.getTextOffset) + 1
      canBeUsage = usage.lines.contains _
    } yield {
      val elems = psiFile.depthFirst().filter(lineNumber andThen canBeUsage).toList
      ElementsInContext(elems, psiFile, document)
    }
}
