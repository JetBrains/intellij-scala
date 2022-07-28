package org.jetbrains.plugins.scala.codeInspection.syntacticClarification

import com.intellij.codeInspection.{LocalQuickFixOnPsiElement, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.AbstractInspection
import org.jetbrains.plugins.scala.lang.transformation.{AbstractTransformer, Transformer}

import scala.annotation.nowarn

@nowarn("msg=" + AbstractInspection.DeprecationText)
class TransformerBasedInspection(@Nls name: String, @Nls solution: String, transformer: AbstractTransformer) extends AbstractInspection(name) {

  override def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Any] = new PartialFunction[PsiElement, Any] {
    override def isDefinedAt(e: PsiElement): Boolean = transformer.isApplicableTo(e)

    override def apply(e: PsiElement): Unit = {
      holder.registerProblem(e, name, new LocalQuickFixOnPsiElement(e) {
        override def invoke(project: Project, psiFile: PsiFile, psiElement: PsiElement, psiElement1: PsiElement): Unit = {
          Transformer.applyTransformerAndReformat(e, psiFile, transformer)
        }

        override def getText: String = solution

        override def getFamilyName: String = getText
      })
    }
  }
}
