package org.jetbrains.plugins.scala.structuralSearch

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.{LeafElement, LeafPsiElement}
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor
import com.intellij.structuralsearch.impl.matcher.handlers.{MatchingHandler, SubstitutionHandler, TopLevelMatchingHandler}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScConstructorInvocation, ScLiteral, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.*
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause, ScParameters, ScTypeParam, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScFunction, ScFunctionDefinition, ScPatternDefinition, ScValueDeclaration, ScValueOrVariable, ScValueOrVariableDeclaration, ScValueOrVariableDefinition, ScVariableDeclaration, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScConstructorOwner, ScEnum, ScObject, ScTemplateDefinition, ScTrait, ScTypeDefinition}
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

  private def optionalInstanceOf[S, T, U](element: T, f: S => U): Option[U] = {
    element match {
      case element: S => Some(f(element))
      case _ => None
    }
  }

  private def extractConstructorInvocations(templateDefinition: ScTemplateDefinition): Seq[PsiElement] = {
    templateDefinition.extendsBlock.templateParents.map(_.parentClauses).getOrElse(PsiElement.EMPTY_ARRAY)
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

  private def matchInAnyOrder(pattern: Seq[PsiElement], other: Seq[PsiElement]): Boolean = {
    globalVisitor.matchInAnyOrder(pattern.toArray[PsiElement], other.toArray[PsiElement])
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
    (typedef, globalVisitor.getElement) match {
      case (enumCase: ScEnumCase, other: ScEnumCase) =>
        matchEnumCase(enumCase, other)
      case (classlike: (ScClass | ScTrait | ScObject), other: (ScClass | ScTrait | ScObject)) =>
        matchClassLike(classlike, other)
      case _ =>
        globalVisitor.setResult(false)
    }
  }

  private def matchClassLike(typedef: ScTypeDefinition, other: ScTypeDefinition): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScTypeDefinition]

    val handler = getHandler(typedef)
    val annotationsMatch = matchInAnyOrder(typedef.annotations, other.annotations)
    val keywordMatch = typedef.keywordPrefix == other.keywordPrefix
    val modifierMatch = checkModifier(typedef.getModifierList.modifiers, other.getModifierList.modifiers)
    val nameMatch = matchTextOrVariable(typedef.getNameIdentifier, other.getNameIdentifier, handler)
    val typeParamsMatch = matchInAnyOrder(typedef.getTypeParameters, other.getTypeParameters)
    val functionsMatch = matchInAnyOrder(typedef.functions, other.functions)
    val constructorsMatch = (typedef, other) match {
      case (typedef: ScConstructorOwner, other: ScConstructorOwner) =>
        matchPrimaryConstructor(typedef.constructor, other.constructor)
      case _ => true
    }

    // match in any order if the body contains only declarations & definitions
    // otherwise fall back to a sequential match
    val primaryConstrBodyMatch = {
      def extractValVar(typedef: ScTypeDefinition): Array[PsiElement] =
        typedef.extendsBlock.templateBody.map(_.getChildren.filter(_.is[ScBlockStatement]).filterNot(_.is[ScFunction, ScTypeDefinition])).getOrElse(PsiElement.EMPTY_ARRAY)
      val bodyPattern = extractValVar(typedef)
      val bodyOther = extractValVar(other)
      if (bodyPattern.forall(_.is[ScValueOrVariableDeclaration, ScValueOrVariableDefinition]))
        matchInAnyOrder(typedef.properties, other.properties)
      else
        matchSequentially(bodyPattern, bodyOther)
    }

    val classesMatch = matchInAnyOrder(typedef.typeDefinitions, other.typeDefinitions)

    val parentsMatch = matchInAnyOrder(extractConstructorInvocations(typedef), extractConstructorInvocations(other))
    val casesMatch = (typedef, other) match {
      case (en: ScEnum, other: ScEnum) => matchInAnyOrder(en.cases, other.cases)
      case _ => true
    }

    globalVisitor.setResult(annotationsMatch && keywordMatch && modifierMatch && nameMatch && parentsMatch && typeParamsMatch
      && primaryConstrBodyMatch && constructorsMatch
      && functionsMatch && classesMatch && casesMatch)
    rememberVarMatchIfResult(handler, other.getNameIdentifier)
  }

  private def matchEnumCase(enCase: ScEnumCase, other: ScEnumCase): Unit = {
    val handler = getHandler(enCase)
    val annotationsMatch = matchInAnyOrder(enCase.annotations, other.annotations)
    val modifierMatch = checkModifier(enCase.getModifierList.modifiers, other.getModifierList.modifiers)
    val nameMatch = matchTextOrVariable(enCase.getNameIdentifier, other.getNameIdentifier, handler)

    val constrMatch = {
      val patternConstr = optionalInstanceOf(enCase, extractConstructorInvocations).getOrElse(Seq())
      val matchConstr = optionalInstanceOf(other, extractConstructorInvocations).getOrElse(Seq())
      matchInAnyOrder(patternConstr, matchConstr)
    }

    globalVisitor.setResult(annotationsMatch && modifierMatch && nameMatch && constrMatch)
    rememberVarMatchIfResult(handler, other.getNameIdentifier)
  }

  private def matchDeclaration(pat: ScValueOrVariable, other: ScValueOrVariable): Boolean = {
    val modifierMatch = checkModifier(pat.getModifierList.modifiers, other.getModifierList.modifiers)
    val annotationsMatch = matchInAnyOrder(pat.getAnnotations, other.getAnnotations)
    val namesMatch = pat.declaredElements.size == other.declaredElements.size
      && (if (pat.declaredElements.size == 1) matchTextOrVariable(pat.declaredElements.head, other.declaredElements.head, getHandler(pat))
    else pat.declaredElements.zip(other.declaredElements).forall((pa, ot) => matchTextOrVariable(pa, ot, getHandler(pa)))
      )
    val typesMatch = matchOptOptional(pat.typeElement, other.typeElement)

    modifierMatch && annotationsMatch && namesMatch && typesMatch
  }

  override def visitPatternDefinition(pat: ScPatternDefinition): Unit = {
    if (!globalVisitor.getElement.is[ScPatternDefinition]) {
      globalVisitor.setResult(false)
      return
    }
    val other = globalVisitor.getElement.asInstanceOf[ScPatternDefinition]
    val declMatch = matchDeclaration(pat, other)
    val exprMatch = matchOptOptional(pat.expr, other.expr)
    globalVisitor.setResult(declMatch && exprMatch)
  }

  override def visitValueDeclaration(pat: ScValueDeclaration): Unit = {
    if (!globalVisitor.getElement.is[ScValueDeclaration, ScPatternDefinition]) {
      globalVisitor.setResult(false)
      return
    }
    val other = globalVisitor.getElement.asInstanceOf[ScValueOrVariable]
    globalVisitor.setResult(matchDeclaration(pat, other))
  }

  override def visitVariableDefinition(pat: ScVariableDefinition): Unit = {
    if (!globalVisitor.getElement.is[ScVariableDefinition]) {
      globalVisitor.setResult(false)
      return
    }
    val other = globalVisitor.getElement.asInstanceOf[ScVariableDefinition]
    val declMatch = matchDeclaration(pat, other)
    val exprMatch = matchOptOptional(pat.expr, other.expr)
    globalVisitor.setResult(declMatch && exprMatch)
  }

  override def visitVariableDeclaration(pat: ScVariableDeclaration): Unit = {
    if (!globalVisitor.getElement.is[ScVariableDeclaration, ScVariableDefinition]) {
      globalVisitor.setResult(false)
      return
    }
    val other = globalVisitor.getElement.asInstanceOf[ScValueOrVariable]
    globalVisitor.setResult(matchDeclaration(pat, other))
  }

  def matchPrimaryConstructor(constr: Option[ScPrimaryConstructor], other: Option[ScPrimaryConstructor]): Boolean = {
    (constr, other) match {
      case (None, _) => true
      case (_, None) => false
      case (Some(constr), Some(other)) =>
        if (constr.parameters.isEmpty) return true

        val modifierMatch = checkModifier(constr.getModifierList.modifiers, other.getModifierList.modifiers)
        val paramsMatch = globalVisitor.`match`(constr.parameterList, other.parameterList)
        val typeParamsMatch = matchInAnyOrder(constr.typeParameters, other.typeParameters)

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
    val annotationsMatch = matchInAnyOrder(fun.annotations, other.annotations)
    val modifierMatch = checkModifier(fun.getModifierList.modifiers, other.getModifierList.modifiers)
    val nameMatch = matchTextOrVariable(fun.getNameIdentifier, other.getNameIdentifier, handler)
    val typeParamsMatch = fun.typeParameters.isEmpty ||
      matchInAnyOrder(fun.typeParameters, other.typeParameters)
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

  def visitTypeParam(typeParam: ScTypeParam): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScTypeParam]

    val handler = getHandler(typeParam)
    val nameMatch = matchTextOrVariable(typeParam.getNameIdentifier, other.getNameIdentifier, handler)
    val flagsMatch = (!typeParam.isCovariant || other.isCovariant) && (!typeParam.isContravariant || other.isContravariant)
    val upperBoundMatch = matchOptOptional(typeParam.upperTypeElement, other.upperTypeElement)
    val lowerBoundMatch = matchOptOptional(typeParam.lowerTypeElement, other.lowerTypeElement)

    globalVisitor.setResult(nameMatch && flagsMatch && upperBoundMatch && lowerBoundMatch)
    rememberVarMatchIfResult(handler, other.getNameIdentifier)
  }

  override def visitTypeElement(te: ScTypeElement): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScTypeElement]
    val identPat = te.getFirstChild match {
      case leaf: LeafPsiElement => leaf
      case el => el.getFirstChild
    }
    val identOther = other.getFirstChild match {
      case leaf: LeafPsiElement => leaf
      case el => el.getFirstChild
    }

    val handler = getHandler(te)
    globalVisitor.setResult(matchTextOrVariable(identPat, identOther, handler))
    rememberVarMatchIfResult(handler, identOther)
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
    if (!globalVisitor.getElement.is[ScConstructorInvocation]) {
      globalVisitor.setResult(false)
      return
    }
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

  override def visitParameter(parameter: ScParameter): Unit = {
    if (!globalVisitor.getElement.is[ScParameter]) {
      globalVisitor.setResult(false)
      return
    }
    val other = globalVisitor.getElement.asInstanceOf[ScParameter]

    val handler = getHandler(parameter)
    val annotationsMatch = matchInAnyOrder(parameter.annotations, other.annotations)
    val modifierMatch = checkModifier(parameter.getModifierList.modifiers, other.getModifierList.modifiers)
    val typeMatch = matchOptOptional(parameter.typeElement, other.typeElement)
    val identMatch = matchTextOrVariable(parameter.getIdentifyingElement, other.getIdentifyingElement, handler)
    val defaultMatch = matchOptOptional(parameter.getDefaultExpression, other.getDefaultExpression)

    val valvarMatch = matchValVar(parameter, other)
    globalVisitor.setResult(annotationsMatch && modifierMatch && typeMatch && identMatch && valvarMatch && defaultMatch)
    rememberVarMatchIfResult(handler, other.getNameIdentifier)
  }

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
    if (!globalVisitor.getElement.is[ScMatch]) {
      globalVisitor.setResult(false)
      return
    }
    val other = globalVisitor.getElement.asInstanceOf[ScMatch]

    val expressionMatch = matchOpt(ms.expression, other.expression)
    val casesMatch = matchSequentially(ms.clauses, other.clauses)
    globalVisitor.setResult(expressionMatch && casesMatch)
  }

  override def visitCaseClause(cc: ScCaseClause): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScCaseClause]

    val handler = getHandler(cc)
    val patternMatch = (cc.pattern, other.pattern) match {
      case (Some (pattern), Some (other)) =>
        handler match {
          case substHand: SubstitutionHandler =>
            substHand.validate(other, globalVisitor.getMatchContext)
          case _ =>
            globalVisitor.`match`(pattern, other)
        }
      case _ => false
    }
    val exprMatch = matchOpt(cc.expr, other.expr)
    val guardMatch = matchOptEqual(cc.guard, other.guard)
    globalVisitor.setResult(patternMatch && exprMatch && guardMatch)
    rememberVarMatchIfResult(handler, other.pattern.get)
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
    val otherV = globalVisitor.getElement
    getHandler(refPat) match {
      case substHand: SubstitutionHandler =>
        if (globalVisitor.setResult(substHand.validate(otherV, context)))
          substHand.addResult(otherV, context)
        return
      case _ =>
    }

    if (!otherV.is[ScReferenceExpression]) {
      globalVisitor.setResult(false)
      return
    }
    val other = otherV.asInstanceOf[ScReferenceExpression]

    val qualifierMatch = matchOptEqual(refPat.qualifier, other.qualifier)
    val nameMatch = globalVisitor.`match`(refPat.nameId, other.nameId)
    globalVisitor.setResult(qualifierMatch && nameMatch)
  }

  override def visitMethodCallExpression(call: ScMethodCall): Unit = visitMethodInvocation(call)

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

  def matchValVar(typedDefPat: ScTypedDefinition, typedDefMatch: ScTypedDefinition): Boolean = {
    (!typedDefPat.isVal || typedDefMatch.isVal) && (!typedDefPat.isVar || typedDefMatch.isVar)
  }

  override def visitTuple(tuple: ScTuple): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScTuple]
    globalVisitor.setResult(matchSequentially(tuple.exprs, other.exprs))
  }

  override def visitScalaElement(element: ScalaPsiElement): Unit = {
    element match {
      case typeParam: ScTypeParam => visitTypeParam(typeParam)
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