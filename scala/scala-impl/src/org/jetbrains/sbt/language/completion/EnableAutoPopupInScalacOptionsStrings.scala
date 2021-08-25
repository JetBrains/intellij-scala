package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.ThreeState
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScInfixExpr}
import org.jetbrains.sbt.language.utils.SbtScalacOptionUtils.matchesScalacOptions

class EnableAutoPopupInScalacOptionsStrings extends CompletionConfidence {
  override def shouldSkipAutopopup(contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState =
    contextElement.getParent match {
      case str: ScStringLiteral =>
        str.getParent match {
          case expr: ScInfixExpr if matchesScalacOptions(expr.left) && expr.operation.refName == "+=" =>
            ThreeState.NO
          case args: ScArgumentExprList =>
            args.getParent.getParent match {
              case expr: ScInfixExpr if matchesScalacOptions(expr.left) && expr.operation.refName == "++=" =>
                ThreeState.NO
              case _ => ThreeState.UNSURE
            }
          case _ => ThreeState.UNSURE
        }
      case _ => ThreeState.UNSURE
    }
}
