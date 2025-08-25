package org.jetbrains.plugins.scala.structuralSearch

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.{LeafElement, LeafPsiElement}
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor
import com.intellij.structuralsearch.impl.matcher.handlers.{MatchingHandler, SubstitutionHandler, TopLevelMatchingHandler}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScConstructorInvocation, ScFieldId, ScLiteral, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScBlockExpr, ScDo, ScFor, ScForBinding, ScGenerator, ScGuard, ScIf, ScInfixExpr, ScMatch, ScMethodCall, ScParenthesisedExpr, ScReferenceExpression, ScTuple, ScWhile}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScConstructorOwner, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaPsiElement}
import org.jetbrains.plugins.scala.util.EnumSet.{EnumSet, EnumSetOps}
import org.jetbrains.plugins.scala.extensions.ObjectExt


class ScalaMatchingVisitor(globalVisitor: GlobalMatchingVisitor) extends ScalaElementVisitor {

  private def matchOpt(patO: Option[PsiElement], psiO: Option[PsiElement]): Boolean = {
    (patO, psiO) match {
      case (Some(pat), Some(psi)) =>
        globalVisitor.`match`(pat, psi)
      case _ => false
    }
  }

  private def matchOptOptional(patO: Option[PsiElement], psiO: Option[PsiElement]): Boolean = {
    (patO, psiO) match {
      case (Some(pat), Some(psi)) =>
        globalVisitor.`match`(pat, psi)
      case (None, _) => true
      case _ => false
    }
  }

  private def matchOptEqual(patO: Option[PsiElement], psiO: Option[PsiElement]): Boolean = {
    (patO, psiO) match {
      case (Some(pat), Some(psi)) =>
        globalVisitor.`match`(pat, psi)
      case (None, None) => true
      case _ => false
    }
  }

  private def matchBody(patO: Option[PsiElement], psiO: Option[PsiElement]): Boolean = {
      (patO, psiO) match {
        case (Some(pat: ScBlockExpr), Some(psi: ScBlockExpr)) =>
          globalVisitor.matchSequentially(pat.getFirstChild, psi.getFirstChild)
        case (Some(pat: ScBlockExpr), Some(psi)) =>
          globalVisitor.matchSequentially(pat.statements.toArray[PsiElement], Array(psi))
        case (Some(pat), Some(psi: ScBlockExpr)) =>
          globalVisitor.matchSequentially(Array(pat), psi.statements.toArray[PsiElement])
        case (Some(pat), Some(psi)) =>
          globalVisitor.`match`(pat, psi)
        case _ => false
      }
  }

  private def checkModifier(pat: EnumSet[ScalaModifier], psi: EnumSet[ScalaModifier]): Boolean =
    pat.toArray.forall(p => psi.contains(p))

  private def matchTextOrVariable(el1: PsiElement, el2: PsiElement, handler: MatchingHandler): Boolean = {
    handler match {
      case substHandler: SubstitutionHandler => substHandler.validate(el2, globalVisitor.getMatchContext)
      case topLevel: TopLevelMatchingHandler =>
        topLevel.getDelegate match {
          case substHandler: SubstitutionHandler => substHandler.validate(el2, globalVisitor.getMatchContext)
          case _ => globalVisitor.matchText(el1, el2)
        }
      case _ => globalVisitor.matchText(el1, el2)
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
        val typeParamsMatch = globalVisitor.matchSequentially(constr.typeParameters.toArray[PsiElement], other.typeParameters.toArray[PsiElement])

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
      globalVisitor.matchSequentially(fun.typeParameters.toArray[PsiElement], other.typeParameters.toArray[PsiElement])
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
    val argsMatch = constrInvocation.arguments.isEmpty || globalVisitor.matchSequentially(constrInvocation.arguments.toArray[PsiElement], other.arguments.toArray[PsiElement])
    globalVisitor.setResult(typeMatch && argsMatch)
  }

  override def visitParameterClause(clause: ScParameterClause): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScParameterClause]

    globalVisitor.setResult(
      globalVisitor.matchSequentially(clause.parameters.toArray[PsiElement], other.parameters.toArray[PsiElement])
    )
  }

  override def visitParameters(parameters: ScParameters): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScParameters]

    globalVisitor.setResult(
      globalVisitor.matchSequentially(parameters.clauses.toArray[PsiElement], other.clauses.toArray[PsiElement])
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
    val annotationsMatch = globalVisitor.matchSequentially(parameter.annotations.toArray[PsiElement], other.annotations.toArray[PsiElement])
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
    val ifPsi = globalVisitor.getElement.asInstanceOf[ScIf]

    val condMatch = matchOpt(ifPat.condition, ifPsi.condition)
    val thenMatch = matchBody(ifPat.thenExpression, ifPsi.thenExpression)
    val elseMatch = (ifPat.elseExpression, ifPsi.elseExpression) match {
      case (None, None) => true
      case _ => matchBody(ifPat.elseExpression, ifPsi.elseExpression)
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
      case (Some (enumPat), Some (enumPsi)) =>
        globalVisitor.matchSequentially(enumPat.enumerators.toArray[PsiElement], enumPsi.enumerators.toArray[PsiElement])
      case _ => false
    }
    val bodyMatch = matchBody(expr.body, other.body)

    globalVisitor.setResult(yieldMatch && enumeratorsMatch && bodyMatch)
  }

  override def visitMatch(ms: ScMatch): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScMatch]

    val expressionMatch = matchOpt(ms.expression, other.expression)
    val casesMatch = globalVisitor.matchSequentially(ms.clauses.toArray[PsiElement], other.clauses.toArray[PsiElement])
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
    val thisPat = call.thisExpr
    val invokedPat = call.getInvokedExpr.getLastChild
    val parsPat = call.argumentExpressions

    val other = globalVisitor.getElement.asInstanceOf[MethodInvocation]
    val thisPsi = other.thisExpr
    val invokedPsi = other.getInvokedExpr.getLastChild
    val parsPsi = other.argumentExpressions

    val thisMatch = globalVisitor.matchOptionally(thisPat.orNull, thisPsi.orNull)
    val methodMatch = globalVisitor.`match`(invokedPat, invokedPsi)
    val parsMatch = globalVisitor.matchSequentially(parsPat.toArray[PsiElement], parsPsi.toArray[PsiElement])
    globalVisitor.setResult(thisMatch && methodMatch && parsMatch)
  }

  def matchFieldId(fieldIdPat: ScFieldId, fieldIdMatch: ScFieldId): Boolean = {
    matchTextOrVariable(fieldIdPat.getNameIdentifier, fieldIdMatch.getNameIdentifier, getHandler(fieldIdPat))
    false
  }

  override def visitTuple(tuple: ScTuple): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScTuple]
    globalVisitor.setResult(globalVisitor.matchSequentially(tuple.exprs.toArray[PsiElement], other.exprs.toArray[PsiElement]))
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