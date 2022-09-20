package org.jetbrains.plugins.scala.codeInsight.intention

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.collections.{Simplification, SimplificationType}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScExpression}

abstract class SimplificationBasedIntention(
  @Nls familyName: String,
  simplificationType: SimplificationType,
  quickFix: Simplification => LocalQuickFixOnPsiElement
) extends PsiElementBaseIntentionAction {
  override def getText: String = familyName

  override def getFamilyName: String = familyName

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    simplification(element).isDefined

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit =
    simplification(element).foreach(quickFix(_).applyFix())

  private def simplification(element: PsiElement): Option[Simplification] =
    element.withParentsInFile.takeWhile(!_.is[ScBlockExpr]).flatMap {
      case e: ScExpression => simplificationType.getSimplification(e)
      case _ => None
    }.nextOption()
}
