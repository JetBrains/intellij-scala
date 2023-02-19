package org.jetbrains.sbt.language.completion

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.util.ThreeState
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScInfixExpr}
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
            if infix.left.textMatches("libraryDependencies") && infix.operation.refName == "+=" ||
              SBT_MODULE_ID_TYPE.contains(infix.`type`().getOrAny.canonicalText) ||
              SBT_ORG_ARTIFACT.contains(infix.`type`().getOrAny.canonicalText) =>
            ThreeState.NO
          case infix: ScInfixExpr if infix.operation.refName == ":=" =>
            infix.left match {
              case subInfix: ScInfixExpr
                if subInfix.operation.refName == "/" && subInfix.right.textMatches("scalaVersion") =>
                ThreeState.NO
              case other if other.textMatches("scalaVersion") =>
                ThreeState.NO
              case _ =>
                ThreeState.UNSURE
            }
          case argList: ScArgumentExprList =>
            val grandParent = argList.getParent.getParent
            grandParent match {
              case patDef: ScPatternDefinition if SBT_MODULE_ID_TYPE.exists(patDef.`type`().getOrAny.canonicalText.contains) =>
                ThreeState.NO
              case infix: ScInfixExpr if infix.left.textMatches("libraryDependencies") && infix.operation.refName == "++=" =>
                ThreeState.NO
              case _ =>
                ThreeState.UNSURE
            }
          case _ => ThreeState.UNSURE
        }
      case _ => ThreeState.UNSURE
    }

  }
}
