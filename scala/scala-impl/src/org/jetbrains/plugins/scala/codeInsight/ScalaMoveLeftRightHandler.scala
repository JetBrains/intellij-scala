package org.jetbrains.plugins.scala.codeInsight

import com.intellij.codeInsight.editorActions.moveLeftRight.MoveElementLeftRightHandler
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, childOf}
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScInfixLikeTypeElement, ScInfixTypeElement, ScTupleTypeElement, ScTypeArgs}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameterClause, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
  * @author Nikolay.Tropin
  */
class ScalaMoveLeftRightHandler extends MoveElementLeftRightHandler {
  override def getMovableSubElements(element: PsiElement): Array[PsiElement] = {
    element match {
      case argList: ScArgumentExprList =>
        argList.exprs.toArray
      case paramClause: ScParameterClause =>
        paramClause.parameters.toArray
      case ta: ScTypeArgs =>
        ta.typeArgs.toArray
      case tp: ScTypeParamClause =>
        tp.typeParameters.toArray
      case pa: ScPatternArgumentList =>
        pa.patterns.toArray
      case t: ScTuple =>
        t.exprs.toArray
      case t: ScTupleTypeElement =>
        t.components.toArray
      case tp: ScTuplePattern =>
        tp.patternList.toSeq.flatMap(_.patterns).toArray
      case InfixElement(_, ref1, _) childOf InfixElement(_, ref2, _)
        if ref1.forall(isNonAssignOperator) && ref2.forall(isNonAssignOperator) &&
          operatorPriority(ref1) == operatorPriority(ref2) =>
        Array.empty
      case infix@InfixElement(_, ref, _) if ref.forall(isNonAssignOperator) =>
        collectInfixParts(infix, operatorPriority(ref))
      case cc: ScCaseClauses =>
        cc.caseClauses.toArray
      case oper childOf InfixElement(_, ref, _) if ref.contains(oper) =>
        val priority = operatorPriority(ref)
        val maxInfix = oper.parentsInFile.takeWhile {
          case InfixElement(_, r, _) if priority == operatorPriority(r) => true
          case _ => false
        }.toSeq.last
        maxInfix.parentsInFile.map(getMovableSubElements).find(_.nonEmpty).getOrElse(Array.empty)
      case _ =>
        Array.empty
    }
  }

  private def operatorPriority(ref: Option[ScReferenceElement]): Int = {
    ref match {
      case Some(refExpr: ScReferenceExpression) => ParserUtils.priority(refExpr.refName)
      case _ => -1
    }
  }

  private def collectInfixParts(elem: PsiElement, priority: Int): Array[PsiElement] = {
    elem match {
      case InfixElement(left, ref, right) if ref.forall(isNonAssignOperator) && priority == operatorPriority(ref) =>
        collectInfixParts(left, priority) ++ collectInfixParts(right, priority)
      case _ => Array(elem)
    }
  }

  private object InfixElement {
    def unapply(elem: PsiElement): Option[(PsiElement, Option[ScReferenceElement], PsiElement)] = elem match {
      case ScInfixExpr(l, o, r) => Some((l, Option(o), r))
      case ip: ScInfixPattern => ip.rightOption.map(r => (ip.left, Option(ip.operation), r))
      case it: ScInfixTypeElement => it.rightOption.map(r => (it.left, Option(it.operation), r))
      case it: ScInfixLikeTypeElement => it.rightOption.map(r => (it.left, None, r))
      case _ => None
    }
  }

  private def isNonAssignOperator(ref: ScReferenceElement): Boolean = {
    val name = ref.refName
    StringUtil.isNotEmpty(name) && ScalaNamesUtil.isOpCharacter(name.charAt(0)) && !ParserUtils.isAssignmentOperator(name)
  }
}
