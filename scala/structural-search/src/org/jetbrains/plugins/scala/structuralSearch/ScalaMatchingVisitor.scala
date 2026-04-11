package org.jetbrains.plugins.scala.structuralSearch

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.{LeafElement, LeafPsiElement}
import com.intellij.structuralsearch.impl.matcher.handlers.{DelegatingHandler, MatchingHandler, SubstitutionHandler, TopLevelMatchingHandler}
import com.intellij.structuralsearch.impl.matcher.{GlobalMatchingVisitor, MatchResultImpl}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScCaseClauses, ScPattern, ScPatternArgumentList, ScPatterns, ScTypePattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScDependentFunctionTypeElement, ScLiteralTypeElement, ScMatchTypeCase, ScMatchTypeCases, ScMatchTypeElement, ScNamedTupleTypeComponent, ScNamedTupleTypeElement, ScParameterizedTypeElement, ScParenthesisedTypeElement, ScPolyFunctionTypeElement, ScTypeElement, ScTypeProjection}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScConstructorInvocation, ScEnd, ScLiteral, ScPrimaryConstructor, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.*
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScCatchBlock.unapply
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause, ScParameters, ScTypeParam, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScExtension, ScFunction, ScFunctionDefinition, ScPatternDefinition, ScTypeAlias, ScTypeAliasDefinition, ScValue, ScValueDeclaration, ScValueOrVariable, ScValueOrVariableDeclaration, ScValueOrVariableDefinition, ScVariable, ScVariableDeclaration, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportOrExportStmt, ScImportSelector, ScImportSelectors}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScConstructorOwner, ScDerivesClauseOwner, ScEnum, ScGiven, ScGivenAlias, ScGivenAliasDeclaration, ScGivenAliasDefinition, ScGivenDefinition, ScObject, ScTemplateDefinition, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackageLike, ScalaElementVisitor, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.JavaIdentifier
import org.jetbrains.plugins.scala.util.EnumSet.{EnumSet, EnumSetOps}

import scala.collection.immutable.ArraySeq

class ScalaMatchingVisitor(globalVisitor: GlobalMatchingVisitor) extends ScalaElementVisitor {

  private def matchOpt(patternO: Option[PsiElement], otherO: Option[PsiElement]): Boolean = {
    (patternO, otherO) match {
      case (Some(pattern), Some(other)) =>
        globalVisitor.`match`(pattern, other)
      case _ => false
    }
  }

  private def matchOptOptional(patternO: Option[PsiElement], otherO: Option[PsiElement]): Boolean = {
    patternO match {
      case None => true
      case Some(_) => matchSequentially(patternO.toSeq, otherO.toSeq)
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

  private def extractConstructorInvocations(extBlock: ScExtendsBlock): Seq[ScConstructorInvocation] = {
    extBlock.templateParents.map(_.parentClauses).getOrElse(Seq())
  }

  private def matchBody(patternO: Option[PsiElement], otherO: Option[PsiElement]): Boolean = {
    val patternStatements = patternO match {
      case Some(pattern: ScBlockExpr) => pattern.statements
      case Some(stmt) => Seq(stmt)
      case None => Seq()
    }
    val otherStatements = otherO match {
      case Some(body: ScBlockExpr) => body.statements
      case Some(stmt) => Seq(stmt)
      case None => Seq()
    }
    matchSequentially(patternStatements, otherStatements)
  }

  private def matchSequentially(pattern: Seq[PsiElement], other: Seq[PsiElement]): Boolean = {
    globalVisitor.matchSequentially(pattern.toArray[PsiElement], other.toArray[PsiElement])
  }

  private def matchInAnyOrder(pattern: Seq[PsiElement], other: Seq[PsiElement]): Boolean = {
    globalVisitor.matchInAnyOrder(pattern.toArray[PsiElement], other.toArray[PsiElement])
  }

  private def checkModifier(pattern: EnumSet[ScalaModifier], other: EnumSet[ScalaModifier]): Boolean =
    pattern.toArray.forall(p => other.contains(p))

  private def matchTextOrVariable(pattern: Option[PsiElement], other: Option[PsiElement], handler: MatchingHandler): Boolean = {
    (pattern, other) match {
      case (Some(pat), Some(ot)) => matchTextOrVariable(pat, ot, handler)
      case _ => false
    }
  }
  private def matchTextOrVariable(pattern: PsiElement, other: PsiElement, handler: MatchingHandler): Boolean = {
    substHandle(handler, other, () => globalVisitor.matchText(pattern, other))
  }

  private def getHandler(element: PsiElement) =
    globalVisitor.getMatchContext.getPattern.getHandler(element)

  private def rememberVarMatchIfResult(handler: MatchingHandler, matchedEl: PsiElement): Unit = {
    if (globalVisitor.getResult) {
      substHandle(handler, matchedEl, () => false)
    }
  }

  // class, trait, enum, ...
  override def visitTypeDefinition(typedef: ScTypeDefinition): Unit = {
    (typedef, globalVisitor.getElement) match {
      case (enumCase: ScEnumCase, other: ScEnumCase) =>
        matchEnumCase(enumCase, other)
      case (classlike: (ScClass | ScTrait | ScObject | ScGivenDefinition), other: (ScClass | ScTrait | ScObject | ScGivenDefinition)) =>
        matchClassLike(classlike, other)
      case _ =>
        super.visitTypeDefinition(typedef)
    }
  }

  private def matchClassLike(typedef: ScTemplateDefinition, other: ScTemplateDefinition): Unit = {
    val handler = getHandler(typedef)
    val context = globalVisitor.getMatchContext
    val isTypedVar = context.getPattern.isTypedVar(typedef.getNameIdentifier)

    context.pushResult()
    try {
      def typeDefMatch = (typedef, other) match {
        case (td: ScTypeDefinition, ot: ScTypeDefinition) =>
          def annotationsMatch = matchInAnyOrder(td.annotations, ot.annotations)
          def keywordMatch = td.keywordPrefix == ot.keywordPrefix
          def modifierMatch = checkModifier(td.getModifierList.modifiers, ot.getModifierList.modifiers)
          annotationsMatch && keywordMatch && modifierMatch
        case (_: ScGiven, _: ScGiven) => true
        case _ => false
      }
      def nameMatch = matchTextOrVariable(Option(typedef.nameId), Option(other.nameId), handler)
      def typeParamsMatch = matchInAnyOrder(ArraySeq.unsafeWrapArray(typedef.getTypeParameters), ArraySeq.unsafeWrapArray(other.getTypeParameters))
      def constructorsMatch = (typedef, other) match {
        case (typedef: ScConstructorOwner, other: ScConstructorOwner) =>
          matchPrimaryConstructor(typedef.constructor, other.constructor)
        case _ => true
      }

      def casesMatch = (typedef, other) match {
        case (en: ScEnum, other: ScEnum) => matchInAnyOrder(en.cases, other.cases)
        case _ => true
      }
      def derivesMatch = (typedef, other) match {
        case (en: ScDerivesClauseOwner, other: ScDerivesClauseOwner) =>
          (en.derivesClause, other.derivesClause) match {
            case (Some(der), Some(derOther)) =>
              matchInAnyOrder(der.derivedReferences, derOther.derivedReferences)
            case (None, _) => true
            case _ => false
          }
        case _ => true
      }

      def extendsBlockMatch = globalVisitor.`match`(typedef.extendsBlock, other.extendsBlock)

      globalVisitor.setResult(typeDefMatch && nameMatch && typeParamsMatch
        && constructorsMatch && casesMatch && derivesMatch && extendsBlockMatch)
    } finally {
      scopeMatch(typedef, isTypedVar, other, Some(other.nameId), typedef)
    }
  }

  private def visitExtendsBlock(pattern: ScExtendsBlock): Unit = {
    val found = globalVisitor.getElement.asInstanceOf[ScExtendsBlock]
    globalVisitor.setResult(matchExtendsBlock(pattern, found))
  }

  private def matchExtendsBlock(pattern: ScExtendsBlock, found: ScExtendsBlock): Boolean = {
    def parentsMatch = matchInAnyOrder(extractConstructorInvocations(pattern), extractConstructorInvocations(found))
    def functionsMatch = matchInAnyOrder(pattern.functions, found.functions)
    def classesMatch = matchInAnyOrder(pattern.typeDefinitions, found.typeDefinitions)
    def exportsMatch = matchInAnyOrder(
      pattern.templateBody.map(_.getExportStatements).getOrElse(Seq.empty),
      found.templateBody.map(_.getExportStatements).getOrElse(Seq.empty))
    parentsMatch && functionsMatch && classesMatch && exportsMatch && primaryConstrBodyMatch(pattern.templateBody, found.templateBody)
  }

  private def primaryConstrBodyMatch(templBody: Option[ScTemplateBody], other: Option[ScTemplateBody]) = {
    // match in any order if the body contains only declarations & definitions
    // otherwise fall back to a sequential match
    def extractStatements(body: Option[ScTemplateBody]): Seq[PsiElement] =
      body.map(_.getChildren.toSeq.filter(_.is[ScBlockStatement]).filterNot(_.is[ScFunction, ScTypeDefinition])).getOrElse(Seq())

    val bodyPattern = extractStatements(templBody)
    val bodyOther = extractStatements(other)
    if (bodyPattern.forall(_.is[ScValueOrVariableDeclaration, ScValueOrVariableDefinition]))
      matchInAnyOrder(templBody.map(_.properties).getOrElse(Seq()), other.map(_.properties).getOrElse(Seq()))
    else
      matchSequentially(bodyPattern, bodyOther)
  }

  private def visitTemplateBody(templBody: ScTemplateBody): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScTemplateBody]
    def functionsMatch = matchInAnyOrder(templBody.functions, other.functions)
    def classesMatch = matchInAnyOrder(templBody.typeDefinitions, other.typeDefinitions)
    def exportsMatch = matchSequentially(templBody.getExportStatements, other.getExportStatements)
    globalVisitor.setResult(functionsMatch && classesMatch && primaryConstrBodyMatch(Some(templBody), Some(other)) && exportsMatch)
  }

  private def matchEnumCase(enCase: ScEnumCase, other: ScEnumCase): Unit = {
    def handler = getHandler(enCase)
    def annotationsMatch = matchInAnyOrder(enCase.annotations, other.annotations)
    def modifierMatch = checkModifier(enCase.getModifierList.modifiers, other.getModifierList.modifiers)
    def nameMatch = matchTextOrVariable(enCase.getNameIdentifier, other.getNameIdentifier, handler)

    def constrMatch = {
      val patternConstr = extractConstructorInvocations(enCase.extendsBlock)
      val matchConstr = extractConstructorInvocations(other.extendsBlock)
      matchInAnyOrder(patternConstr, matchConstr)
    }

    globalVisitor.setResult(annotationsMatch && modifierMatch && nameMatch && constrMatch)
  }

  private def matchDefinition(pat: ScValueOrVariable): Unit = {
    if (!globalVisitor.getElement.is[ScValueOrVariable]) {
      globalVisitor.setResult(false)
      return
    }
    val context = globalVisitor.getMatchContext
    val isTypedVar = pat.declaredElements.size == 1 && context.getPattern.isTypedVar(pat.declaredNames.head)
    val other = globalVisitor.getElement.asInstanceOf[ScValueOrVariable]

    context.pushResult()
    try {
      def valvarMatch = (pat.is[ScVariable] && other.is[ScVariable]) || (pat.is[ScValue] && other.is[ScValue])
      def modifierMatch = checkModifier(pat.getModifierList.modifiers, other.getModifierList.modifiers)
      def annotationsMatch = matchInAnyOrder(ArraySeq.unsafeWrapArray(pat.getAnnotations), ArraySeq.unsafeWrapArray(other.getAnnotations))
      def namesMatch = pat.declaredElements.size == other.declaredElements.size
        && (if (pat.declaredElements.size == 1)
        matchTextOrVariable(pat.declaredElements.head, other.declaredElements.head, getHandler(pat))
      else pat.declaredElements.zip(other.declaredElements).forall((pa, ot) => matchTextOrVariable(pa, ot, getHandler(pa)))
        )
      def typesMatch = matchOptOptional(pat.typeElement, other.typeElement)
      def exprMatch = {
        val patExp = pat match {
          case patDef: ScValueOrVariableDefinition => patDef.expr
          case _ => None
        }
        val otherExp = other match {
          case otherDef: ScValueOrVariableDefinition => otherDef.expr
          case _ => None
        }
        matchOptOptional(patExp, otherExp)
      }

      globalVisitor.setResult(valvarMatch && modifierMatch && annotationsMatch && namesMatch && typesMatch && exprMatch)
    } finally {
      scopeMatch(pat, isTypedVar, other, other.declaredElements.headOption, pat)
    }
  }

  override def visitPatternDefinition(pat: ScPatternDefinition): Unit = {
    matchDefinition(pat)
  }

  override def visitValueDeclaration(pat: ScValueDeclaration): Unit = {
    matchDefinition(pat)
  }

  override def visitVariableDefinition(pat: ScVariableDefinition): Unit = {
    matchDefinition(pat)
  }

  override def visitVariableDeclaration(pat: ScVariableDeclaration): Unit = {
    matchDefinition(pat)
  }

  def matchPrimaryConstructor(constr: Option[ScPrimaryConstructor], other: Option[ScPrimaryConstructor]): Boolean = {
    (constr, other) match {
      case (None, _) => true
      case (_, None) => false
      case (Some(constr), Some(other)) =>
        if (constr.parameters.isEmpty) return true

        def modifierMatch = checkModifier(constr.getModifierList.modifiers, other.getModifierList.modifiers)
        def paramsMatch = globalVisitor.`match`(constr.parameterList, other.parameterList)
        def typeParamsMatch = matchInAnyOrder(constr.typeParameters, other.typeParameters)

        modifierMatch && typeParamsMatch && paramsMatch
    }
  }

  override def visitFunction(fun: ScFunction): Unit = {
    if (!globalVisitor.getElement.is[ScFunction]) {
      globalVisitor.setResult(false)
      return
    }
    val context = globalVisitor.getMatchContext
    val isTypedVar = context.getPattern.isTypedVar(fun.name)
    val other = globalVisitor.getElement.asInstanceOf[ScFunction]

    val handler = getHandler(fun)
    context.pushResult()
    try {
      def annotationsMatch = matchInAnyOrder(fun.annotations, other.annotations)
      def modifierMatch = checkModifier(fun.getModifierList.modifiers, other.getModifierList.modifiers)
      def nameMatch = matchTextOrVariable(Option(fun.nameId).getOrElse(fun.getIdentifyingElement), Option(other.nameId).getOrElse(other.getIdentifyingElement), handler)
      def typeParamsMatch = fun.typeParameters.isEmpty ||
        matchInAnyOrder(fun.typeParameters, other.typeParameters)
      def paramsMatch = globalVisitor.`match`(fun.paramClauses, other.paramClauses)
      def rTypeMatch = matchOptOptional(fun.returnTypeElement, other.returnTypeElement)
      def bodyMatch = {
        val patBody = fun match {
          case declPat: ScFunctionDefinition => declPat.body
          case _ => None
        }
        val otherBody = other match {
          case declOther: ScFunctionDefinition => declOther.body
          case _ => None
        }
        if patBody.isEmpty then true else matchBody(patBody, otherBody)
      }

      globalVisitor.setResult(annotationsMatch && modifierMatch && nameMatch && typeParamsMatch && paramsMatch && rTypeMatch && bodyMatch)
    } finally {
      scopeMatch(fun, isTypedVar, other, Option(other.nameId), fun)
    }
  }

  override def visitGiven(pattern: ScGiven): Unit = {
    val found = globalVisitor.getElement match {
      case g: ScGiven => g
      case _ =>
        globalVisitor.setResult(false)
        return
    }
    val context = globalVisitor.getMatchContext
    context.pushResult()
    try {
      def nameMatches = matchOptOptional(pattern.nameElement, found.nameElement)

      def annotationsMatch = matchInAnyOrder(pattern.annotations, found.annotations)

      def modifierMatch = checkModifier(pattern.getModifierList.modifiers, found.getModifierList.modifiers)

      def getGivenTypes(g: ScGiven): Seq[ScTypeElement] = g match {
        case g: ScGivenDefinition => extractConstructorInvocations(g.extendsBlock).map(_.typeElement)
        case g: ScGivenAlias => g.typeElement.toSeq
      }

      def typeMatches = matchInAnyOrder(getGivenTypes(pattern), getGivenTypes(found))

      def typeParamsMatch = {
        val patternTypeParams = pattern.typeParameters
        patternTypeParams.isEmpty || matchInAnyOrder(patternTypeParams, found.typeParameters)
      }

      def paramsMatch = pattern.parameters.isEmpty || matchSequentially(pattern.parameters, found.parameters)

      def bodyMatches = pattern match {
        case _: ScGivenAliasDeclaration => true
        case pattern: ScGivenAliasDefinition =>
          found match {
            case found: ScGivenAliasDefinition => matchBody(pattern.body, found.body)
            case found: ScGivenAliasDeclaration => matchBody(pattern.body, None)
            case _ => false
          }
        case pattern: ScGivenDefinition =>
          found match {
            case found: ScGivenDefinition => matchExtendsBlock(pattern.extendsBlock, found.extendsBlock)
            case _ => false
          }
        case _ => false
      }

      globalVisitor.setResult(nameMatches && annotationsMatch && modifierMatch && typeMatches && typeParamsMatch && paramsMatch && bodyMatches)
    } finally {
      scopeMatch(pattern, typedVar = false, found, Option(found.nameId), pattern)
    }
  }

  private def visitTypeParameter(typeParam: ScTypeParam): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScTypeParam]
    val context = globalVisitor.getMatchContext
    val isTypedVar = context.getPattern.isTypedVar(typeParam.name)

    context.pushResult()
    try {
      val handler = getHandler(typeParam)
      def nameMatch = matchTextOrVariable(Option(typeParam.nameId), Option(other.nameId), handler)
      def flagsMatch = (!typeParam.isCovariant || other.isCovariant) && (!typeParam.isContravariant || other.isContravariant)
      def lowerBoundMatch = matchOptOptional(typeParam.lowerTypeElement, other.lowerTypeElement)
      def upperBoundMatch = matchOptOptional(typeParam.upperTypeElement, other.upperTypeElement)
      globalVisitor.setResult(nameMatch && flagsMatch && lowerBoundMatch && upperBoundMatch)
    } finally {
      scopeMatch(typeParam, isTypedVar, other, Option(other.nameId), typeParam)
    }
  }

  private def unwrapTypeParenthesis(te: ScTypeElement): Option[ScTypeElement] = {
    te match {
      case paren: ScParenthesisedTypeElement => paren.innerElement.flatMap(unwrapTypeParenthesis)
      case _ => Some(te)
    }
  }

  override def visitTypeElement(teI: ScTypeElement): Unit = {
    val te = (unwrapTypeParenthesis(teI) match {
      case None =>
        globalVisitor.setResult(true)
        return
      case Some(c) => c
    }).asInstanceOf[ScalaPsiElement] // Can also be ScTypeElement
    val other =
      (globalVisitor.getElement match {
        case typeElement: ScTypeElement =>
          unwrapTypeParenthesis(typeElement) match {
            case None =>
              globalVisitor.setResult(false)
              return
            case Some(c) => c
          }
        case typeParam: ScTypeParam => Some(typeParam)
        case _ =>
          globalVisitor.setResult(false)
          return
      }).asInstanceOf[ScalaPsiElement]

    val identPat = te.getFirstChild match {
      case leaf: LeafPsiElement => leaf
      case el => el.getFirstChild
    }
    val identOther = other.getFirstChild match {
      case leaf: LeafPsiElement => leaf
      case el => el.getFirstChild
    }
    val handler = getHandler(te)

    def nameMatch = matchTextOrVariable(identPat, identOther, handler)
    def parameterizedMatch = {
      val patternArgs = te match {
        case patternDef: ScParameterizedTypeElement => Some(patternDef.typeArgList.typeArguments.flatMap(_.typeElement))
        case _ => None
      }
      val matchArgs = other match {
        case otherDef: ScParameterizedTypeElement => Some(otherDef.typeArgList.typeArguments.flatMap(_.typeElement))
        case _ => None
      }
      (patternArgs, matchArgs) match {
        case (Some(pArgs), Some(mArgs)) => matchSequentially(pArgs, mArgs)
        case (Some(_), _) => false
        case (_, _) => true
      }
    }

    globalVisitor.setResult(nameMatch && parameterizedMatch)
  }

  // lambdas
  override def visitFunctionExpression(stmt: ScFunctionExpr): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScFunctionExpr]
    def paramsMatch = matchSequentially(stmt.parameters, other.parameters)
    def resMatch = matchOpt(stmt.result, other.result)
    globalVisitor.setResult(paramsMatch && resMatch)
  }

  override def visitPolyFunctionExpression(fun: ScPolyFunctionExpr): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScPolyFunctionExpr]
    def paramsMatch = matchSequentially(fun.typeParameters, other.typeParameters)
    def resMatch = matchOpt(fun.result, other.result)
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
    val context = globalVisitor.getMatchContext
    val isTypedVar = context.getPattern.isTypedVar(constrInvocation.typeElement.getText)

    context.pushResult()
    try {
      val typeMatch = globalVisitor.`match`(constrInvocation.typeElement, other.typeElement)
      def argsMatch = constrInvocation.arguments.isEmpty || matchSequentially(constrInvocation.arguments, other.arguments)
      globalVisitor.setResult(typeMatch && argsMatch)
    } finally {
      scopeMatch(constrInvocation, isTypedVar, other, Some(other.typeElement), constrInvocation)
    }
  }

  override def visitParameterizedTypeElement(parameterized: ScParameterizedTypeElement): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScParameterizedTypeElement]

    def elementMatch = globalVisitor.`match`(parameterized.typeElement, other.typeElement)
    def parametersMatch = matchSequentially(
      parameterized.typeArgList.typeArguments.flatMap(_.typeElement),
      other.typeArgList.typeArguments.flatMap(_.typeElement)
    )
    globalVisitor.setResult(elementMatch && parametersMatch)
  }

  override def visitParameters(parameters: ScParameters): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScParameters]
    val clauses = parameters.clauses
    globalVisitor.setResult(
      clauses match {
        case Seq(clause) if !clause.isImplicit =>
          matchSequentially(parameters.params, other.params)
        case _ =>
          matchSequentially(clauses, other.clauses)
      }
    )
  }

  override def visitParameterClause(clause: ScParameterClause): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScParameterClause]
    val implicitnessMatches = !clause.isImplicit || other.isImplicit

    globalVisitor.setResult(
      implicitnessMatches && matchSequentially(clause.parameters, other.parameters)
    )
  }

  override def visitTypeParameterClause(clause: ScTypeParamClause): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScTypeParamClause]

    globalVisitor.setResult(
      matchSequentially(clause.typeParameters, other.typeParameters)
    )
  }

  override def visitParameter(parameter: ScParameter): Unit = {
    if (!globalVisitor.getElement.is[ScParameter]) {
      globalVisitor.setResult(false)
      return
    }
    val other = globalVisitor.getElement.asInstanceOf[ScParameter]
    val context = globalVisitor.getMatchContext
    val isTypedVar = context.getPattern.isTypedVar(parameter.getNameIdentifier)

    val handler = getHandler(parameter)
    context.pushResult()
    try {
      def annotationsMatch = matchInAnyOrder(parameter.annotations, other.annotations)
      def modifierMatch = checkModifier(parameter.getModifierList.modifiers, other.getModifierList.modifiers)
      def typeMatch = matchOptOptional(parameter.typeElement, other.typeElement)
      def identMatch = matchTextOrVariable(Option(parameter.nameId).orElse(Some(parameter.getIdentifyingElement)), Option(other.nameId).orElse(Some(other.getIdentifyingElement)), handler)
      def defaultMatch = matchOptOptional(parameter.getDefaultExpression, other.getDefaultExpression)
      def valvarMatch = matchValVar(parameter, other)

      globalVisitor.setResult(annotationsMatch && modifierMatch && typeMatch && identMatch && valvarMatch && defaultMatch)
    } finally {
      scopeMatch(parameter, isTypedVar, other, Option(other.nameId), parameter)
    }
  }

  override def visitIf(ifPat: ScIf): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScIf]

    def condMatch = matchOpt(ifPat.condition, other.condition)
    def thenMatch = matchBody(ifPat.thenExpression, other.thenExpression)
    def elseMatch = (ifPat.elseExpression, other.elseExpression) match {
      case (None, None) => true
      case _ => matchBody(ifPat.elseExpression, other.elseExpression)
    }
    globalVisitor.setResult(condMatch && thenMatch && elseMatch)
  }

  override def visitWhile(ws: ScWhile): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScWhile]

    def condMatch = matchOpt(ws.condition, other.condition)
    def bodyMatch = matchBody(ws.expression, other.expression)

    globalVisitor.setResult(condMatch && bodyMatch)
  }

  override def visitDo(doStmt: ScDo): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScDo]

    def condMatch = matchOpt(doStmt.condition, other.condition)
    def bodyMatch = matchBody(doStmt.body, other.body)

    globalVisitor.setResult(condMatch && bodyMatch)
  }

  override def visitFor(expr: ScFor): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScFor]

    def yieldMatch = expr.isYield == other.isYield
    def enumeratorsMatch = (expr.enumerators, other.enumerators) match {
      case (Some(enumPattern), Some(enumOther)) =>
        matchSequentially(enumPattern.enumerators, enumOther.enumerators)
      case _ => false
    }
    def bodyMatch = matchBody(expr.body, other.body)

    globalVisitor.setResult(yieldMatch && enumeratorsMatch && bodyMatch)
  }

  override def visitTry(tryStmt: ScTry): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScTry]

    def exprMatch = matchBody(tryStmt.expression, other.expression)
    def catchMatch = matchOptOptional(tryStmt.catchBlock, other.catchBlock)
    def finallyMatch = matchOptOptional(tryStmt.finallyBlock, other.finallyBlock)
    globalVisitor.setResult(exprMatch && catchMatch && finallyMatch)
  }

  override def visitCatchBlock(c: ScCatchBlock): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScCatchBlock]
    globalVisitor.setResult((unapply(c), unapply(other)) match {
      case (Some(cc), Some(otherCC)) => matchInAnyOrder(cc.caseClauses, otherCC.caseClauses)
      case (None, _) => true
      case _ => false
    })
  }

  def visitFinally(finallyBlock: ScFinallyBlock): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScFinallyBlock]
    globalVisitor.setResult(matchBody(finallyBlock.expression, other.expression))
  }

  override def visitMatch(ms: ScMatch): Unit = {
    if (!globalVisitor.getElement.is[ScMatch]) {
      globalVisitor.setResult(false)
      return
    }
    val other = globalVisitor.getElement.asInstanceOf[ScMatch]

    def expressionMatch = matchOpt(ms.expression, other.expression)
    def casesMatch = matchSequentially(ms.clauses, other.clauses)
    globalVisitor.setResult(expressionMatch && casesMatch)
  }

  override def visitCaseClauses(ccs: ScCaseClauses): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScCaseClauses]
    globalVisitor.setResult(
      matchSequentially(ccs.caseClauses, other.caseClauses)
    )
  }

  override def visitCaseClause(cc: ScCaseClause): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScCaseClause]

    val handler = getHandler(cc)
    val context = globalVisitor.getMatchContext
    val isTypedVar = cc.pattern.exists(_.bindings.nonEmpty) && context.getPattern.isTypedVar(cc.pattern.map(_.bindings.head.name).getOrElse(""))

    context.pushResult()
    try {
      val patternMatch = (cc.pattern, other.pattern) match {
        case (Some(pattern), Some(other)) =>
          handler match {
            case substHand: SubstitutionHandler =>
              substHand.handle(other, globalVisitor.getMatchContext)
            case _ =>
              globalVisitor.`match`(pattern, other)
          }
        case _ => false
      }
      val guardMatch = matchOptOptional(cc.guard, other.guard)
      val exprMatch = matchOptOptional(cc.expr, other.expr)
      globalVisitor.setResult(patternMatch && exprMatch && guardMatch)
    } finally {
      scopeMatch(cc, isTypedVar, other, other.pattern, cc)
    }
  }

  private def visitMatchTypeElement(matchTypeElement: ScMatchTypeElement): Unit = {
    if (!globalVisitor.getElement.is[ScMatchTypeElement]) {
      globalVisitor.setResult(false)
      return
    }
    val other = globalVisitor.getElement.asInstanceOf[ScMatchTypeElement]

    def expressionMatch = globalVisitor.`match`(matchTypeElement.scrutineeTypeElement, other.scrutineeTypeElement)
    def casesMatch = matchOpt(matchTypeElement.cases, other.cases)
    globalVisitor.setResult(expressionMatch && casesMatch)
  }

  private def visitMatchTypeCases(matchTypeCases: ScMatchTypeCases): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScMatchTypeCases]
    globalVisitor.setResult(matchSequentially(matchTypeCases.cases, other.cases))
  }

  private def visitMatchTypeCase(cc: ScMatchTypeCase): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScMatchTypeCase]

    val handler = getHandler(cc)
    val context = globalVisitor.getMatchContext
    val isTypedVar = context.getPattern.isTypedVar(cc.pattern.map(_.getText).getOrElse(""))

    context.pushResult()
    try {
      val patternMatch = (cc.pattern, other.pattern) match {
        case (Some(pattern), Some(other)) =>
          handler match {
            case substHand: SubstitutionHandler =>
              substHand.handle(other, globalVisitor.getMatchContext)
            case _ =>
              globalVisitor.`match`(pattern, other)
          }
        case _ => false
      }
      val resultMatch = matchOpt(cc.result, other.result)
      globalVisitor.setResult(patternMatch && resultMatch)
    } finally {
      scopeMatch(cc, isTypedVar, other, other.pattern, cc)
    }
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
        if (globalVisitor.setResult(substHand.handle(other, context)))
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
    def modifierMatch = checkModifier(alias.getModifierList.modifiers, other.getModifierList.modifiers)
    def nameMatch = matchTextOrVariable(alias.getNameIdentifier, other.getNameIdentifier, handler)
    def typeParamsMatch = matchInAnyOrder(alias.typeParameters, other.typeParameters)
    def lowerBoundMatch = matchOptOptional(alias.lowerTypeElement, other.lowerTypeElement)
    def upperBoundMatch = matchOptOptional(alias.upperTypeElement, other.upperTypeElement)
    def typeMatch = {
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
    val otherV = globalVisitor.getElement

    otherV match {
      case other: ScReferenceExpression =>
        refPat.qualifier match {
          case None =>
            globalVisitor.setResult(substHandle(getHandler(refPat), other, () => globalVisitor.matchText(refPat, other)))
          case Some(_) =>
            val qualifierMatch = other.qualifier match {
              case None => matchOptOptional(refPat.qualifier, other.qualifier)
              case Some(_) => matchOptEqual(refPat.qualifier, other.qualifier)
            }
            val nameMatch = refPat.nameId.getTextLength > 0 && substHandle(getHandler(refPat), other.nameId, () => globalVisitor.`match`(refPat.nameId, other.nameId))
            globalVisitor.setResult(qualifierMatch && nameMatch)
        }
      case other =>
        globalVisitor.setResult(refPat.qualifier.isEmpty && substHandle(getHandler(refPat), otherV, () => globalVisitor.matchText(refPat, other)))
    }
  }

  override def visitNamedTuple(tuple: ScNamedTuple): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScNamedTuple]
    globalVisitor.setResult(matchInAnyOrder(tuple.components, other.components))
  }

  private def visitNamedTupleExprComponent(tupleComp: ScNamedTupleExprComponent): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScNamedTupleExprComponent]

    val handler = getHandler(tupleComp)
    def nameMatch = matchTextOrVariable(tupleComp.getNameIdentifier, other.getNameIdentifier, handler)
    def valMatch = matchOpt(tupleComp.expr, other.expr)
    globalVisitor.setResult(nameMatch && valMatch)
  }

  override def visitNamedTupleTypeElement(tuple: ScNamedTupleTypeElement): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScNamedTupleTypeElement]
    globalVisitor.setResult(matchInAnyOrder(tuple.components, other.components))
  }

  private def visitNamedTupleTypeComponent(tupleComp: ScNamedTupleTypeComponent): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScNamedTupleTypeComponent]

    val handler = getHandler(tupleComp)
    def nameMatch = matchTextOrVariable(tupleComp.getNameIdentifier, other.getNameIdentifier, handler)
    def typeMatch = matchOpt(tupleComp.typeElement, other.typeElement)
    globalVisitor.setResult(nameMatch && typeMatch)
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

    def refMatch = globalVisitor.`match`(call.referencedExpr, other.referencedExpr)
    def argsMatch = if call.typeArguments.isEmpty then true else matchSequentially(call.typeArguments, other.typeArguments)
    globalVisitor.setResult(refMatch && argsMatch)
  }

  def visitMethodInvocation(call: MethodInvocation): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[MethodInvocation]

    val unwrapMethodName: ScExpression => PsiElement = {
      case ref: ScReferenceExpression => ref.nameId
      case expr => expr
    }

    val handler = getHandler(call)
    def thisMatch = matchOptOptional(call.thisExpr, other.thisExpr)
    def methodMatch = matchTextOrVariable(unwrapMethodName(call.getInvokedExpr), unwrapMethodName(other.getInvokedExpr), handler)
    def parsMatch = matchSequentially(call.argumentExpressions, other.argumentExpressions)
    globalVisitor.setResult(thisMatch && methodMatch && parsMatch)
  }

  override def visitNewTemplateDefinition(templ: ScNewTemplateDefinition): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScNewTemplateDefinition]
    def constrMatch = matchOpt(templ.firstConstructorInvocation, other.firstConstructorInvocation)
    def bodyMatch = matchOpt(templ.extendsBlock.templateBody, other.extendsBlock.templateBody)
    globalVisitor.setResult(templ.isAnonymous == other.isAnonymous && (if templ.isAnonymous then bodyMatch else constrMatch))
  }

  override def visitReturn(ret: ScReturn): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScReturn]
    globalVisitor.setResult(matchOptEqual(ret.expr, other.expr))
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

  override def visitTypeProjection(proj: ScTypeProjection): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScTypeProjection]

    def typeMatch = globalVisitor.`match`(proj.typeElement, other.typeElement)
    def refMatch = globalVisitor.`match`(proj.nameId, other.nameId)
    globalVisitor.setResult(typeMatch && refMatch)
  }

  override def visitArgumentExprList(args: ScArgumentExprList): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScArgumentExprList]
    globalVisitor.setResult(matchSequentially(args.exprs, other.exprs))
  }

  override def visitTypedExpr(stmt: ScTypedExpression): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScTypedExpression]

    def exprMatch = globalVisitor.`match`(stmt.expr, other.expr)

    def typeMatch = matchOptEqual(stmt.typeElement, other.typeElement)

    globalVisitor.setResult(exprMatch && typeMatch)
  }

  private def visitExtension(ext: ScExtension): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScExtension]

    def targetParMatch = matchOpt(ext.targetParameter, other.targetParameter)

    def targetTypeMatch = matchOpt(ext.targetTypeElement, other.targetTypeElement)

    def methodsMatch = matchInAnyOrder(ext.extensionMethods, other.extensionMethods)

    globalVisitor.setResult(targetParMatch && targetTypeMatch && methodsMatch)
  }

  private def visitDependantFunctionTypeElement(dependantFunctionType: ScDependentFunctionTypeElement): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScDependentFunctionTypeElement]
    def parMatch = globalVisitor.`match`(dependantFunctionType.parameterClause, other.parameterClause)
    def retTypeMatch = matchOpt(dependantFunctionType.returnTypeElement, other.returnTypeElement)
    globalVisitor.setResult(parMatch && retTypeMatch)
  }

  private def visitPolyFunctionTypeElement(polyFunctionTypeElement: ScPolyFunctionTypeElement): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScPolyFunctionTypeElement]

    def parMatch = matchSequentially(polyFunctionTypeElement.typeParameters, other.typeParameters)

    def retTypeMatch = matchOpt(polyFunctionTypeElement.resultTypeElement, other.resultTypeElement)

    globalVisitor.setResult(parMatch && retTypeMatch)
  }

  private def visitBlock(block: ScBlock): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScBlock]
    globalVisitor.setResult(matchSequentially(block.statements, other.statements))
  }

  override def visitSelfInvocation(self: ScSelfInvocation): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScSelfInvocation]
    globalVisitor.setResult(matchSequentially(self.arguments, other.arguments))
  }

  override def visitPatternArgumentList(args: ScPatternArgumentList): Unit = {
    val other = globalVisitor.getElement.asInstanceOf[ScPatternArgumentList]
    globalVisitor.setResult(matchSequentially(args.patterns, other.patterns))
  }

  override def visitScalaElement(element: ScalaPsiElement): Unit = {
    element match {
      case typeParam: ScTypeParam => visitTypeParameter(typeParam)
      case finallyBlock: ScFinallyBlock => visitFinally(finallyBlock)
      case tuple: ScNamedTupleExprComponent => visitNamedTupleExprComponent(tuple)
      case tuple: ScNamedTupleTypeComponent => visitNamedTupleTypeComponent(tuple)
      case extBlock: ScExtendsBlock => visitExtendsBlock(extBlock)
      case templBody: ScTemplateBody => visitTemplateBody(templBody)
      case extension: ScExtension => visitExtension(extension)
      case dependantFunctionType: ScDependentFunctionTypeElement => visitDependantFunctionTypeElement(dependantFunctionType)
      case polyFunctionType: ScPolyFunctionTypeElement => visitPolyFunctionTypeElement(polyFunctionType)
      case matchTypeElement: ScMatchTypeElement => visitMatchTypeElement(matchTypeElement)
      case matchTypeCases: ScMatchTypeCases => visitMatchTypeCases(matchTypeCases)
      case matchTypeCase: ScMatchTypeCase => visitMatchTypeCase(matchTypeCase)
      case block: ScBlock => visitBlock(block)
      case self: ScSelfInvocation => visitSelfInvocation(self)
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
      case _: (ScPattern | ScPatterns | ScTypePattern
         | ScThisReference | ScStableCodeReference | ScUnitExpr | ScSuperReference | ScLiteralTypeElement | ScEnd
         | ScImportExpr | ScImportSelector | ScImportSelectors | ScPackageLike | ScImportOrExportStmt | ScUnderscoreSection) =>
        globalVisitor.matchSons(elementPat, other)
      case _ =>
        // fallback that should not happen
        globalVisitor.matchSons(elementPat, other)
    }
  }

  private def scopeMatch(patternNode: PsiElement, typedVar: Boolean, matchNode: PsiElement, ident: Option[PsiElement], pattern: PsiElement): Unit = {
    if (typedVar) {
      ident.foreach(
        id => globalVisitor.getMatchContext.getResult.addChild(new MatchResultImpl(ScalaStructuralSearchProfile.SCOPE_ID, id.getText, id, 0, 0, false))
      )
      globalVisitor.getMatchContext.getResult.addChild(new MatchResultImpl(ScalaStructuralSearchProfile.PATTERN_CONTEXT, pattern.getText, pattern, 0, 0, false))
    }
    getHandler(patternNode) match {
      case tlh: TopLevelMatchingHandler =>
        val ident = new JavaIdentifier(patternNode)
        globalVisitor.getMatchContext.getPattern.setHandler(ident, tlh.getDelegate)
        globalVisitor.scopeMatch(ident, typedVar, matchNode)
      case _ => globalVisitor.scopeMatch(patternNode, typedVar, matchNode)
    }
  }

  private def substHandle(handler: MatchingHandler, other: PsiElement, otherwise: () => Boolean): Boolean = {
    asSubstitutionHandler(handler).fold(otherwise()) {
      substHandler => substHandler.handle(other, globalVisitor.getMatchContext)
    }
  }

  private def asSubstitutionHandler(handler: MatchingHandler): Option[SubstitutionHandler] =
    handler match {
      case substHand: SubstitutionHandler => Some(substHand)
      case delHand: DelegatingHandler =>
        delHand.getDelegate match {
          case substHand: SubstitutionHandler => Some(substHand)
          case _ => None
        }
      case _ => None
    }
}
