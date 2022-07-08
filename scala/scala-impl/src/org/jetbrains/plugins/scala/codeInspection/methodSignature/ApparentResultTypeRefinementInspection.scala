package org.jetbrains.plugins.scala
package codeInspection
package methodSignature

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScCompoundTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDeclaration}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

final class ApparentResultTypeRefinementInspection extends AbstractMethodSignatureInspection {

  import ApparentResultTypeRefinementInspection._

  override protected def isApplicable(function: ScFunction): Boolean =
    function.isInstanceOf[ScFunctionDeclaration] && typeComponents(function).isDefined

  override protected def findProblemElement(function: ScFunction): Option[PsiElement] =
    function.returnTypeElement

  override protected def createQuickFix(function: ScFunction): Option[LocalQuickFix] = {
    val quickFix = new AbstractFixOnPsiElement(
      ScalaInspectionBundle.message("insert.missing.assignment"),
      function
    ) {
      override protected def doApplyFix(function: ScFunction)(implicit project: Project): Unit = for {
        (lastType, refinement) <- typeComponents(function)
        endIndex = lastType.getTextRange.getEndOffset - function.getTextRange.getStartOffset

        text = s"${function.getText.substring(0, endIndex)} = ${refinement.getText}"
        method = ScalaPsiElementFactory.createMethodFromText(text)
      } function.replace(method)
    }

    Some(quickFix)
  }
}

object ApparentResultTypeRefinementInspection {

  private def typeComponents(function: ScFunction) = for {
    ScCompoundTypeElement(types, Some(refinement)) <- function.returnTypeElement
    lastType <- types.lastOption
  } yield (lastType, refinement)
}
