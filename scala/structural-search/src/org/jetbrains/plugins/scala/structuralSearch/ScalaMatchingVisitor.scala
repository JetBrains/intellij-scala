package org.jetbrains.plugins.scala.structuralSearch

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor
import com.intellij.structuralsearch.impl.matcher.handlers.{MatchingHandler, SubstitutionHandler, TopLevelMatchingHandler}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScConstructorInvocation, ScFieldId, ScLiteral, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.*
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScConstructorOwner, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaPsiElement}
import org.jetbrains.plugins.scala.util.EnumSet.{EnumSet, EnumSetOps}


class ScalaMatchingVisitor(globalVisitor: GlobalMatchingVisitor) extends ScalaElementVisitor {

  private def matchOpt(patternO: Option[PsiElement], otherO: Option[PsiElement]): Boolean = {
    (patternO, otherO) match {
      case (Some(pattern), Some(other)) =>
        globalVisitor.`match`(pattern, other)
      case _ => false
    }
  }

  private def matchOptOptional(patternO: Option[PsiElement], otherO: Option[PsiElement]): Boolean = {
    (patternO, otherO) match {
      case (Some(pattern), Some(other)) =>
        globalVisitor.`match`(pattern, other)
      case (None, _) => true
      case _ => false
    }
  }

  private def matchOptEqual(patternO: Option[PsiElement], otherO: Option[PsiElement]): Boolean = {
    (patternO, otherO) match {
      case (Some(pattern), Some(other)) =>
        globalVisitor.`match`(pattern, other)
      case (None, None) => true
      case _ => false
    }
  }

  private def matchBody(patternO: Option[PsiElement], otherO: Option[PsiElement]): Boolean = {
    (patternO, otherO) match {
      case (Some(pattern: ScBlockExpr), Some(other: ScBlockExpr)) =>
        globalVisitor.matchSequentially(pattern.getFirstChild, other.getFirstChild)
      case (Some(pattern: ScBlockExpr), Some(other)) =>
        globalVisitor.matchSequentially(pattern.statements.toArray[PsiElement], Array(other))
      case (Some(pattern), Some(other: ScBlockExpr)) =>
        globalVisitor.matchSequentially(Array(pattern), other.statements.toArray[PsiElement])
      case (Some(pattern), Some(other)) =>
        globalVisitor.`match`(pattern, other)
      case _ => false
    }
  }

  private def matchSequentially(pattern: Seq[PsiElement], other: Seq[PsiElement]): Boolean = {
    globalVisitor.matchSequentially(pattern.toArray[PsiElement], other.toArray[PsiElement])
  }

  private def checkModifier(pattern: EnumSet[ScalaModifier], other: EnumSet[ScalaModifier]): Boolean =
    pattern.toArray.forall(p => other.contains(p))

  private def matchTextOrVariable(pattern: PsiElement, other: PsiElement, handler: MatchingHandler): Boolean = {
    handler match {
      case substHandler: SubstitutionHandler => substHandler.validate(other, globalVisitor.getMatchContext)
      case topLevel: TopLevelMatchingHandler =>
        topLevel.getDelegate match {
          case substHandler: SubstitutionHandler => substHandler.validate(other, globalVisitor.getMatchContext)
          case _ => globalVisitor.matchText(pattern, other)
        }
      case _ => globalVisitor.matchText(pattern, other)
    }
  }

  private def getHandler(element: PsiElement) =
    globalVisitor.getMatchContext.getPattern.getHandler(element)

  private def rememberVarMatchIfResult(handler: MatchingHandler, matchedEl: PsiElement): Unit = {
    if (globalVisitor.getResult) {
      handler match {
        case substHandler: SubstitutionHandler =>
          substHandler.handle(matchedEl, globalVisitor.getMatchContext)
        case topLevel: TopLevelMatchingHandler =>
          topLevel.getDelegate match {
            case substHandler: SubstitutionHandler =>
              substHandler.handle(matchedEl, globalVisitor.getMatchContext)
            case _ =>
          }
        case _ =>
      }
    }
  }

  // class, trait, enum, ...
  override def visitTypeDefinition(typedef: ScTypeDefinition): Unit = {
    if (!globalVisitor.getElement.is[ScTypeDefinition]) {
      globalVisitor.setResult(false)
      return
    }
    val other = globalVisitor.getElement.asInstanceOf[ScTypeDefinition]

    val handler = getHandler(typedef)
    val annotationsMatch = globalVisitor.matchInAnyOrder(typedef.annotations.toArray[PsiElement], other.annotations.toArray[PsiElement])
    val keywordMatch = typedef.keywordPrefix == other.keywordPrefix
    val modifierMatch = checkModifier(typedef.getModifierList.modifiers, other.getModifierList.modifiers)
    val nameMatch = matchTextOrVariable(typedef.getNameIdentifier, other.getNameIdentifier, handler)
    val functionsMatch = globalVisitor.matchInAnyOrder(typedef.functions.toArray[PsiElement], other.functions.toArray[PsiElement])
    val constructorsMatch = (typedef, other) match {
      case (typedef: ScConstructorOwner, other: ScConstructorOwner) =>
        matchPrimaryConstructor(typedef.constructor, other.constructor)
      case _ => true
    }

    // TODO find a strategy how to deal with properties
    //    // match all field declarations and do primary constructor afterwards?
    //    val propertiesMatch = globalVisitor.matchInAnyOrder(
    //      typedef.allVals.map(_.namedElement).toArray[PsiElement],
    //      other.allVals.map(_.namedElement).toArray[PsiElement])

    globalVisitor.setResult(annotationsMatch && keywordMatch && modifierMatch && nameMatch && functionsMatch && constructorsMatch)
    rememberVarMatchIfResult(handler, other.getNameIdentifier)
  }

  def matchPrimaryConstructor(constr: Option[ScPrimaryConstructor], other: Option[ScPrimaryConstructor]): Boolean = {
    (constr, other) match {
      case (None, _) => true
      case (_, None) => false
      case (Some(constr), Some(other)) =>
        if (constr.parameters.isEmpty) return true

        val modifierMatch = checkModifier(constr.getModifierList.modifiers, other.getModifierList.modifiers)
        val paramsMatch = globalVisitor.`match`(constr.parameterList, other.parameterList)
        val typeParamsMatch = matchSequentially(constr.typeParameters, other.typeParameters)

        modifierMatch && typeParamsMatch && paramsMatch
    }
  }

  override def visitFunction(fun: ScFunction): Unit = {
    if (!globalVisitor.getElement.is[ScFunction]) {
      globalVisitor.setResult(false)
      return
    }
    val other = globalVisitor.getElement.asInstanceOf[ScFunction]

    val handler = getHandler(fun)
    val annotationsMatch = globalVisitor.matchInAnyOrder(fun.annotations.toArray[PsiElement], other.annotations.toArray[PsiElement])
    val modifierMatch = checkModifier(fun.getModifierList.modifiers, other.getModifierList.modifiers)
    val nameMatch = matchTextOrVariable(fun.getNameIdentifier, other.getNameIdentifier, handler)
    val typeParamsMatch = fun.typeParameters.isEmpty ||
      matchSequentially(fun.typeParameters, other.typeParameters)
    val paramsMatch = globalVisitor.`match`(fun.paramClauses, other.paramClauses)
    val rTypeMatch = matchOptOptional(fun.returnTypeElement, other.returnTypeElement)
    val bodyMatch = {
      fun match {
        case declPat: ScFunctionDefinition =>
          other match {
            case declOther: ScFunctionDefinition =>
              matchBody(declPat.body, declOther.body)
            case _ => false
          }
        case _ => true
      }
    }

    globalVisitor.setResult(annotationsMatch && modifierMatch && nameMatch && typeParamsMatch && paramsMatch && rTypeMatch && bodyMatch)
    rememberVarMatchIfResult(handler, other.getNameIdentifier)
  }

  override def visitAnnotation(annotation: ScAnnotation): Unit = {
    if (!globalVisitor.getElement.is[ScAnnotation]) {
      globalVisitor.setResult(false)
      return
    }
    val other = globalVisitor.getElement.asInstanceOf[ScAnnotation]
    globalVisitor.setResult(globalVisitor.`match`(annotation.constructorInvocation, other.constructorInvocation))
  }

  override def visitConstructorInvocation(constrInvocation: ScConstructorInvocation): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScConstructorInvocation]

    val typeMatch = globalVisitor.`match`(constrInvocation.typeElement, other.typeElement)
    val argsMatch = constrInvocation.arguments.isEmpty || matchSequentially(constrInvocation.arguments, other.arguments)
    globalVisitor.setResult(typeMatch && argsMatch)
  }

  override def visitParameters(parameters: ScParameters): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScParameters]

    globalVisitor.setResult(
      if (parameters.clauses.size == 1)
        matchSequentially(parameters.params, other.params)
      else
        matchSequentially(parameters.clauses, other.clauses)
    )
  }

  override def visitParameterClause(clause: ScParameterClause): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScParameterClause]

    globalVisitor.setResult(
      matchSequentially(clause.parameters, other.parameters)
    )
  }

  // TODO check default parameters
  override def visitParameter(parameter: ScParameter): Unit = {
    if (!globalVisitor.getElement.is[ScParameter]) {
      globalVisitor.setResult(false)
      return
    }
    val other = globalVisitor.getElement.asInstanceOf[ScParameter]

    val handler = getHandler(parameter)
    val annotationsMatch = globalVisitor.matchInAnyOrder(parameter.annotations.toArray[PsiElement], other.annotations.toArray[PsiElement])
    val modifierMatch = checkModifier(parameter.getModifierList.modifiers, other.getModifierList.modifiers)
    val typeMatch = matchOptOptional(parameter.typeElement, other.typeElement)
    val identMatch = matchTextOrVariable(parameter.getIdentifyingElement, other.getIdentifyingElement, handler)
    parameter.getDefaultExpression

    val valvarMatch = parameter.isVal == other.isVal && parameter.isVal == other.isVal

    globalVisitor.setResult(annotationsMatch && modifierMatch && typeMatch && identMatch && valvarMatch)
    rememberVarMatchIfResult(handler, other.getNameIdentifier)
  }

  // TODO annotation

  override def visitIf(ifPat: ScIf): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScIf]

    val condMatch = matchOpt(ifPat.condition, other.condition)
    val thenMatch = matchBody(ifPat.thenExpression, other.thenExpression)
    val elseMatch = (ifPat.elseExpression, other.elseExpression) match {
      case (None, None) => true
      case _ => matchBody(ifPat.elseExpression, other.elseExpression)
    }
    globalVisitor.setResult(condMatch && thenMatch && elseMatch)
  }

  override def visitWhile(ws: ScWhile): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScWhile]

    val condMatch = matchOptOptional(ws.condition, other.condition)
    val bodyMatch = matchBody(ws.expression, other.expression)

    globalVisitor.setResult(condMatch && bodyMatch)
  }

  override def visitDo(doStmt: ScDo): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScDo]

    val condMatch = matchOptOptional(doStmt.condition, other.condition)
    val bodyMatch = matchBody(doStmt.body, other.body)

    globalVisitor.setResult(condMatch && bodyMatch)
  }

  override def visitFor(expr: ScFor): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScFor]

    val yieldMatch = expr.isYield == other.isYield
    val enumeratorsMatch = (expr.enumerators, other.enumerators) match {
      case (Some (enumPattern), Some (enumOther)) =>
        matchSequentially(enumPattern.enumerators, enumOther.enumerators)
      case _ => false
    }
    val bodyMatch = matchBody(expr.body, other.body)

    globalVisitor.setResult(yieldMatch && enumeratorsMatch && bodyMatch)
  }

  override def visitMatch(ms: ScMatch): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScMatch]

    val expressionMatch = matchOpt(ms.expression, other.expression)
    val casesMatch = matchSequentially(ms.clauses, other.clauses)
    globalVisitor.setResult(expressionMatch && casesMatch)
  }

  override def visitCaseClause(cc: ScCaseClause): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScCaseClause]

    val patternMatch = matchOpt(cc.pattern, other.pattern)
    val exprMatch = matchOpt(cc.expr, other.expr)
    val guardMatch = matchOptEqual(cc.guard, other.guard)
    globalVisitor.setResult(patternMatch && exprMatch && guardMatch)
  }

  override def visitGenerator(gen: ScGenerator): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScGenerator]

    val patternMatch = globalVisitor.`match`(gen.pattern, other.pattern)
    val exprMatch = matchOpt(gen.expr, other.expr)
    globalVisitor.setResult(patternMatch && exprMatch)
  }

  override def visitGuard(guard: ScGuard): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScGuard]
    globalVisitor.setResult(matchOpt(guard.expr, other.expr))
  }

  override def visitForBinding(forBinding: ScForBinding): Unit = {
    val other = globalVisitor.getElement
    val context = globalVisitor.getMatchContext
    getHandler(forBinding) match {
      case substHand: SubstitutionHandler =>
        if (globalVisitor.setResult(substHand.validate(other, context)))
          substHand.addResult(other, context)
      case _ =>
        val other = globalVisitor.getElement.asInstanceOf[ScForBinding]

        val patternMatch = globalVisitor.`match`(forBinding.pattern, other.pattern)
        val exprMatch = matchOpt(forBinding.expr, other.expr)
        globalVisitor.setResult(patternMatch && exprMatch)
    }
  }

  override def visitInfixExpression(infixPat: ScInfixExpr): Unit = {
    visitMethodInvocation(infixPat)
  }

  override def visitLiteral(lPat: ScLiteral): Unit =
    globalVisitor.setResult(globalVisitor.matchText(lPat, globalVisitor.getElement))

  override def visitReferenceExpression(refPat: ScReferenceExpression): Unit = {
    val context = globalVisitor.getMatchContext
    val other = globalVisitor.getElement
    getHandler(refPat) match {
      case substHand: SubstitutionHandler =>
        if (globalVisitor.setResult(substHand.validate(other, context)))
          substHand.addResult(other, context)
      case _ =>
        other match {
          case refExpr: ScReferenceExpression => globalVisitor.setResult(globalVisitor.`match`(refPat.getFirstChild, globalVisitor.getElement.getFirstChild))
          case _ => visitElement(refPat)
        }
    }
  }

  override def visitMethodCallExpression(call: ScMethodCall): Unit = visitMethodInvocation(call)

  // TODO do we want to ignore them? (Java does not, Kotlin does so partially)
  override def visitParenthesisedExpr(expr: ScParenthesisedExpr): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScParenthesisedExpr]
    globalVisitor.setResult(matchOpt(expr.innerElement, other.innerElement))
  }

  def visitMethodInvocation(call: MethodInvocation): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[MethodInvocation]

    val thisMatch = matchOptOptional(call.thisExpr, other.thisExpr)
    val methodMatch = globalVisitor.`match`(call.getInvokedExpr.getLastChild, other.getInvokedExpr.getLastChild)
    val parsMatch = matchSequentially(call.argumentExpressions, other.argumentExpressions)
    globalVisitor.setResult(thisMatch && methodMatch && parsMatch)
  }

  def matchFieldId(fieldIdPat: ScFieldId, fieldIdMatch: ScFieldId): Boolean = {
    matchTextOrVariable(fieldIdPat.getNameIdentifier, fieldIdMatch.getNameIdentifier, getHandler(fieldIdPat))
    false
  }

  override def visitTuple(tuple: ScTuple): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScTuple]
    globalVisitor.setResult(matchSequentially(tuple.exprs, other.exprs))
  }

  override def visitScalaElement(element: ScalaPsiElement): Unit = {
    element match {
      case fieldId: ScFieldId => globalVisitor.setResult(matchFieldId(fieldId, globalVisitor.getElement.asInstanceOf[ScFieldId]))
      case _ => visitElement(element)
    }
  }

  override def visitElement(elementPat: PsiElement): Unit = {
    val other = globalVisitor.getElement

    globalVisitor.setResult(
      getHandler(elementPat) match {
        case substHandler: SubstitutionHandler =>
          substHandler.handle(other, globalVisitor.getMatchContext)
        case topLevel: TopLevelMatchingHandler =>
          topLevel.getDelegate match {
            case substHandler: SubstitutionHandler =>
              substHandler.handle(other, globalVisitor.getMatchContext)
            case _ => visitNoHandler(elementPat, other)
          }
        case _ => visitNoHandler(elementPat, other)
      })
  }

  private def visitNoHandler(elementPat: PsiElement, other: PsiElement): Boolean = {
    elementPat match {
      case _: LeafElement => globalVisitor.matchText(elementPat.getText, other.getText)
      case _ => globalVisitor.matchSons(elementPat, other)
    }
  }
}