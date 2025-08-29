package org.jetbrains.plugins.scala.structuralSearch

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.{LeafElement, LeafPsiElement}
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor
import com.intellij.structuralsearch.impl.matcher.handlers.{MatchingHandler, SubstitutionHandler, TopLevelMatchingHandler}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScNamedTupleTypeComponent, ScNamedTupleTypeElement, ScParameterizedTypeElement, ScParenthesisedTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScConstructorInvocation, ScLiteral, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.*
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScCatchBlock.unapply
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause, ScParameters, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScFunction, ScFunctionDefinition, ScPatternDefinition, ScTypeAlias, ScTypeAliasDefinition, ScValueDeclaration, ScValueOrVariable, ScValueOrVariableDeclaration, ScValueOrVariableDefinition, ScVariableDeclaration, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScConstructorOwner, ScDerivesClauseOwner, ScEnum, ScObject, ScTemplateDefinition, ScTrait, ScTypeDefinition}
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
      case (en: ScEnum, other: ScEnum) => {
        matchInAnyOrder(en.cases, other.cases)
      }
      case _ => true
    }
    val derivesMatch = (typedef, other) match {
      case (en: ScDerivesClauseOwner, other: ScDerivesClauseOwner) => {
        (en.derivesClause, other.derivesClause) match {
          case (Some(der), Some(derOther)) =>
            matchInAnyOrder(der.derivedReferences, derOther.derivedReferences)
          case (None, _) => true
          case _ => false
        }
      }
      case _ => true
    }

    globalVisitor.setResult(annotationsMatch && keywordMatch && modifierMatch && nameMatch && parentsMatch && typeParamsMatch
      && primaryConstrBodyMatch && constructorsMatch
      && functionsMatch && classesMatch && casesMatch && derivesMatch)
    rememberVarMatchIfResult(handler, other.getNameIdentifier)
  }

  private def matchEnumCase(enCase: ScEnumCase, other: ScEnumCase): Unit = {
    val handler = getHandler(enCase)
    val annotationsMatch = matchInAnyOrder(enCase.annotations, other.annotations)
    val modifierMatch = checkModifier(enCase.getModifierList.modifiers, other.getModifierList.modifiers)
    val nameMatch = matchTextOrVariable(enCase.getNameIdentifier, other.getNameIdentifier, handler)

    val constrMatch = {
      val patternConstr = enCase match {
        case patternDef: ScTemplateDefinition => extractConstructorInvocations(patternDef)
        case _ => Seq()
      }
      val matchConstr = other match {
        case otherDef: ScTemplateDefinition => extractConstructorInvocations(otherDef)
        case _ => Seq()
      }
      matchInAnyOrder(patternConstr, matchConstr)
    }

    globalVisitor.setResult(annotationsMatch && modifierMatch && nameMatch && constrMatch)
    rememberVarMatchIfResult(handler, other.getNameIdentifier)
  }

  private def matchDeclaration(pat: ScValueOrVariable, other: ScValueOrVariable): Boolean = {
    val modifierMatch = checkModifier(pat.getModifierList.modifiers, other.getModifierList.modifiers)
    val annotationsMatch = matchInAnyOrder(pat.getAnnotations, other.getAnnotations)
    val namesMatch = pat.declaredElements.size == other.declaredElements.size
      && (if (pat.declaredElements.size == 1)
          matchTextOrVariable(pat.declaredElements.head, other.declaredElements.head, getHandler(pat))
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

  private def visitTypeParam(typeParam: ScTypeParam): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScTypeParam]

    val handler = getHandler(typeParam)
    val nameMatch = matchTextOrVariable(typeParam.getNameIdentifier, other.getNameIdentifier, handler)
    val flagsMatch = (!typeParam.isCovariant || other.isCovariant) && (!typeParam.isContravariant || other.isContravariant)
    val lowerBoundMatch = matchOptOptional(typeParam.lowerTypeElement, other.lowerTypeElement)
    val upperBoundMatch = matchOptOptional(typeParam.upperTypeElement, other.upperTypeElement)

    globalVisitor.setResult(nameMatch && flagsMatch && lowerBoundMatch && upperBoundMatch)
    rememberVarMatchIfResult(handler, other.getNameIdentifier)
  }

  private def unwrapTypeParenthesis(te: ScTypeElement): Option[ScTypeElement] = {
    te match {
      case paren: ScParenthesisedTypeElement => paren.innerElement.flatMap(unwrapTypeParenthesis)
      case _ => Some(te)
    }
  }

  override def visitTypeElement(teI: ScTypeElement): Unit = {
    val te = unwrapTypeParenthesis(teI) match {
      case None =>
        globalVisitor.setResult(true)
        return
      case Some(c) => c
    }
    val other = unwrapTypeParenthesis(globalVisitor.getElement.asInstanceOf[ScTypeElement]) match {
      case None =>
        globalVisitor.setResult(false)
        return
      case Some(c) => c
    }

    val identPat = te.getFirstChild match {
      case leaf: LeafPsiElement => leaf
      case el => el.getFirstChild
    }
    val identOther = other.getFirstChild match {
      case leaf: LeafPsiElement => leaf
      case el => el.getFirstChild
    }
    val handler = getHandler(te)

    val nameMatch = matchTextOrVariable(identPat, identOther, handler)
    val parameterizedMatch = {
      val patternArgs = te match {
        case patternDef: ScParameterizedTypeElement => Some(patternDef.typeArgList.typeArgs)
        case _ => None
      }
      val matchArgs = other match {
        case otherDef: ScParameterizedTypeElement => Some(otherDef.typeArgList.typeArgs)
        case _ => None
      }
      (patternArgs, matchArgs) match {
        case (Some(pArgs), Some(mArgs)) => matchSequentially(pArgs, mArgs)
        case (Some(_), _) => false
        case (_, _) => true
      }
    }

    globalVisitor.setResult(nameMatch && parameterizedMatch)
    rememberVarMatchIfResult(handler, identOther)
  }

  // lambdas
  override def visitFunctionExpression(stmt: ScFunctionExpr): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScFunctionExpr]
    val paramsMatch = matchSequentially(stmt.parameters, other.parameters)
    val resMatch = matchOpt(stmt.result, other.result)
    globalVisitor.setResult(paramsMatch && resMatch)
  }

  override def visitPolyFunctionExpression(fun: ScPolyFunctionExpr): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScPolyFunctionExpr]
    val paramsMatch = matchSequentially(fun.typeParameters, other.typeParameters)
    val resMatch = matchOpt(fun.result, other.result)
    globalVisitor.setResult(paramsMatch && resMatch)
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

  override def visitTry(tryStmt: ScTry): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScTry]

    val exprMatch = matchOpt(tryStmt.expression, other.expression)
    val catchMatch = matchOptOptional(tryStmt.catchBlock, other.catchBlock)
    val finallyMatch = matchOptOptional(tryStmt.finallyBlock, other.finallyBlock)
    globalVisitor.setResult(exprMatch && catchMatch && finallyMatch)
  }

  override def visitCatchBlock(c: ScCatchBlock): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScCatchBlock]
    globalVisitor.setResult((unapply(c), unapply(other)) match {
      case (Some (cc), Some (otherCC)) => matchInAnyOrder(cc.caseClauses, otherCC.caseClauses)
      case (None, _) => true
      case _ => false
    })
  }

  def visitFinally(finallyBlock: ScFinallyBlock): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScFinallyBlock]
    globalVisitor.setResult(matchOpt(finallyBlock.expression, other.expression))
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

  override def visitInfixExpression(infixPat: ScInfixExpr): Unit = visitMethodInvocation(infixPat)

  override def visitPrefixExpression(p: ScPrefixExpr): Unit = visitMethodInvocation(p)

  override def visitPostfixExpression(p: ScPostfixExpr): Unit = visitMethodInvocation(p)

  override def visitAssignment(stmt: ScAssignment): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScAssignment]
    globalVisitor.setResult(
      globalVisitor.`match`(stmt.leftExpression, other.leftExpression)
      && matchOpt(stmt.rightExpression, other.rightExpression)
    )
  }

  override def visitTypeAlias(alias: ScTypeAlias): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScTypeAlias]

    val handler = getHandler(alias)
    val modifierMatch = checkModifier(alias.getModifierList.modifiers, other.getModifierList.modifiers)
    val nameMatch = matchTextOrVariable(alias.getNameIdentifier, other.getNameIdentifier, handler)
    val typeParamsMatch = matchInAnyOrder(alias.typeParameters, other.typeParameters)
    val lowerBoundMatch = matchOptOptional(alias.lowerTypeElement, other.lowerTypeElement)
    val upperBoundMatch = matchOptOptional(alias.upperTypeElement, other.upperTypeElement)
    val typeMatch = {
      val aliasType = alias match {
        case aliasDef: ScTypeAliasDefinition => aliasDef.aliasedTypeElement
        case _ => None
      }
      val otherType = other match {
        case otherDef: ScTypeAliasDefinition => otherDef.aliasedTypeElement
        case _ => None
      }
      matchOptOptional(aliasType, otherType)
    }

    globalVisitor.setResult(modifierMatch && nameMatch && typeParamsMatch && lowerBoundMatch && upperBoundMatch && typeMatch)
    rememberVarMatchIfResult(handler, other.getNameIdentifier)
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

    otherV match {
      case other: ScReferenceExpression =>
        val qualifierMatch = matchOptEqual(refPat.qualifier, other.qualifier)
        val nameMatch = globalVisitor.`match`(refPat.nameId, other.nameId)
        globalVisitor.setResult(qualifierMatch && nameMatch)
      case other: PsiElement => globalVisitor.setResult(globalVisitor.matchText(refPat, other))
    }
  }

  override def visitNamedTuple(tuple: ScNamedTuple): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScNamedTuple]
    globalVisitor.setResult(matchInAnyOrder(tuple.components, other.components))
  }

  private def visitNamedTupleExprComponent(tupleComp: ScNamedTupleExprComponent): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScNamedTupleExprComponent]

    val handler = getHandler(tupleComp)
    val nameMatch = matchTextOrVariable(tupleComp.getNameIdentifier, other.getNameIdentifier, handler)
    val valMatch = matchOpt(tupleComp.expr, other.expr)
    globalVisitor.setResult(nameMatch && valMatch)
    rememberVarMatchIfResult(handler, other.getNameIdentifier)
  }

  override def visitNamedTupleTypeElement(tuple: ScNamedTupleTypeElement): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScNamedTupleTypeElement]
    globalVisitor.setResult(matchInAnyOrder(tuple.components, other.components))
  }

  private def visitNamedTupleTypeComponent(tupleComp: ScNamedTupleTypeComponent): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScNamedTupleTypeComponent]

    val handler = getHandler(tupleComp)
    val nameMatch = matchTextOrVariable(tupleComp.getNameIdentifier, other.getNameIdentifier, handler)
    val typeMatch = matchOpt(tupleComp.typeElement, other.typeElement)
    globalVisitor.setResult(nameMatch && typeMatch)
    rememberVarMatchIfResult(handler, other.getNameIdentifier)
  }

  override def visitBlockExpression(block: ScBlockExpr): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScBlockExpr]
    globalVisitor.setResult(matchSequentially(block.exprs, other.exprs))
  }

  override def visitMethodCallExpression(call: ScMethodCall): Unit = visitMethodInvocation(call)

  override def visitParenthesisedExpr(expr: ScParenthesisedExpr): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScParenthesisedExpr]
    globalVisitor.setResult(matchOpt(expr.innerElement, other.innerElement))
  }

  override def visitGenericCallExpression(call: ScGenericCall): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScGenericCall]

    val refMatch = globalVisitor.`match`(call.referencedExpr, other.referencedExpr)
    val argsMatch = if call.arguments.isEmpty then true else matchSequentially(call.arguments, other.arguments)
    globalVisitor.setResult(refMatch && argsMatch)
  }

  def visitMethodInvocation(call: MethodInvocation): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[MethodInvocation]

    val unwrapMethodName: ScExpression => PsiElement = {
      case ref: ScReferenceExpression => ref.nameId
      case expr => expr
    }

    val thisMatch = matchOptOptional(call.thisExpr, other.thisExpr)
    val methodMatch = globalVisitor.`match`(unwrapMethodName(call.getInvokedExpr), unwrapMethodName(other.getInvokedExpr))
    val parsMatch = matchSequentially(call.argumentExpressions, other.argumentExpressions)
    globalVisitor.setResult(thisMatch && methodMatch && parsMatch)
  }

  override def visitNewTemplateDefinition(templ: ScNewTemplateDefinition): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScNewTemplateDefinition]
    globalVisitor.setResult(matchOpt(templ.firstConstructorInvocation, other.firstConstructorInvocation))
  }

  override def visitReturn(ret: ScReturn): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScReturn]
    globalVisitor.setResult(matchOpt(ret.expr, other.expr))
  }

  override def visitThrow(throwStmt: ScThrow): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScThrow]
    globalVisitor.setResult(matchOpt(throwStmt.expression, other.expression))
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
      case finallyBlock: ScFinallyBlock => visitFinally(finallyBlock)
      case tuple: ScNamedTupleExprComponent => visitNamedTupleExprComponent(tuple)
      case tuple: ScNamedTupleTypeComponent => visitNamedTupleTypeComponent(tuple)
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