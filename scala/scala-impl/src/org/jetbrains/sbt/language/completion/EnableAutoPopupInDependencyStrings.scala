package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.ThreeState
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition

class EnableAutoPopupInDependencyStrings extends CompletionConfidence {
  override def shouldSkipAutopopup(contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState = {
    contextElement.getParent match {
      case str: ScStringLiteral =>
        str.getParent match {
          case patDef: ScPatternDefinition
            if SBT_MODULE_ID_TYPE.contains(patDef.`type`().getOrAny.canonicalText) || SBT_ORG_ARTIFACT.contains(patDef.`type`().getOrAny.canonicalText) =>
            ThreeState.NO
          case infix: ScInfixExpr
            if SBT_MODULE_ID_TYPE.contains(infix.`type`().getOrAny.canonicalText) || SBT_ORG_ARTIFACT.contains(infix.`type`().getOrAny.canonicalText) =>
            ThreeState.NO
          case _ => ThreeState.UNSURE
        }
      case _ => ThreeState.UNSURE
    }

  }
}
