package org.jetbrains.plugins.scala.lang.psi.uast.converter

import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil.getParentOfType
import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.{IteratorExt, ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.isUnitTestMode
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.MethodValue
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScCaseClauses, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScUnderScoreSectionUtil.isUnderscoreFunction
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.light.{ScFunctionWrapper, ScPrimaryConstructorWrapper}
import org.jetbrains.plugins.scala.lang.psi.uast.controlStructures._
import org.jetbrains.plugins.scala.lang.psi.uast.declarations._
import org.jetbrains.plugins.scala.lang.psi.uast.expressions.ScUBlockExpression._
import org.jetbrains.plugins.scala.lang.psi.uast.expressions._
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.plugins.scala.lang.psi.uast.possibleSourceTypesCheckIsActive
import org.jetbrains.plugins.scala.lang.psi.uast.utils.NotNothing
import org.jetbrains.plugins.scala.uast.{ScalaUastLanguagePlugin, ScalaUastSourceTypeMapping}
import org.jetbrains.plugins.scala.util.SAMUtil
import org.jetbrains.uast._

import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

/**
  * Main entry point for all Scala to UAST conversions.
  *
  * If you would like to understand it, you may want to fold
  * big matches -- then the common structure will become much
  * clearer.
  */
object Scala2UastConverter extends UastFabrics with ConverterExtension {

  override def convertTo[U <: UElement: ClassTag: NotNothing](
    element: PsiElement,
    @Nullable parent: UElement,
    convertLambdas: Boolean = true
  ): Option[U] = {
    val converted = convertToFree[U](element, convertLambdas)
    converted.map(_.pinTo(parent))
  }

  override def convertWithParentTo[U <: UElement: ClassTag: NotNothing](
    element: PsiElement,
    convertLambdas: Boolean = true
  ): Option[U] = {
    val converted = convertToFree[U](element, convertLambdas)
    converted.map(makeUParentAndPin(element, _))
  }

  override def convertWithParentToUExpressionOrEmpty(
    element: PsiElement,
    convertLambdas: Boolean
  ): UExpression = {
    val converted = convertWithParentTo[UExpression](element, convertLambdas)
    converted.getOrElse {
      val empty = Free.fromConstructor[UExpression](createUEmptyExpression(element = null, _: UElement))
      val pinned = makeUParentAndPin(element, empty)
      pinned
    }
  }

  private def makeUParentAndPin[U <: UElement](sourcePsi: PsiElement,
                                               converted: Free[U]): U =
    converted.pinTo(
      LazyUElement.fromThunk(() => makeUParent(sourcePsi, converted).orNull)
    )

  private def convertToFree[U <: UElement: ClassTag: NotNothing](
    sourcePsi: PsiElement,
    convertLambdas: Boolean = true
  ): Option[Free[U]] = {
    import ConverterUtils._

    val requiredType = implicitly[ClassTag[U]].runtimeClass.asInstanceOf[Class[U]]

    //performance optimization
    if (!isUnitTestMode && !isPossibleToConvert(requiredType, sourcePsi)) {
      return None
    }

    //noinspection GetOrElseNull,ConvertibleToMethodValue
    val elementOpt: Option[Free[UElement]] =
      Option((sourcePsi match {

        // ========================= DECLARATIONS ===============================

        case file: ScalaFile =>
          (_: LazyUElement) =>
            new ScUFile(
              file,
              UastFacade.INSTANCE.findPlugin(file).asInstanceOf[ScalaUastLanguagePlugin]
            )
        case e: ScImportStmt => new ScUImportStatement(e, _)

        case e: ScTypeDefinition            => new ScUClass(e, _)
        case ScUVariable(parent2UField)     => parent2UField(_)
        case e: ScMethodLike if !e.isLocal  => new ScUMethod(e, _)
        case e: ScPrimaryConstructorWrapper => new ScUMethod(e.delegate, _)
        case e: ScFunctionWrapper           => new ScUMethod(e.delegate, _)
        case e: ScParameter                 => new ScUParameter(e, _)
        case e: ScValueOrVariable           => new ScUDeclarationsExpression(e, _)

        case e: ScFunctionDefinition if e.isLocal =>
          ScULocalFunction
            .findContainingTypeDef(e)
            .map(
              tpDef =>
                new ScULocalFunctionDeclarationExpression(
                  e,
                  tpDef,
                  _: LazyUElement
              )
            )
            .orNull

        //Handle case with anonymous class: new SomeClass() { ... }
        //NOTE: we attach `ScUAnonymousClass` UAST element to `ScExtendsBlock` PSI element and not to `ScTemplateBody` because here:
        //com.intellij.codeInspection.NonExtendableApiUsageInspection.NonExtendableApiUsageProcessor.processReference
        //it's assumed that `USimpleNameReferenceExpression` (representing `SomeClass` identifier) will have UClass as it's parent
        case eb: ScExtendsBlock if eb.isAnonymousClass =>
          //noinspection ScalaUnusedSymbol
          (for {
            nt @ (td: ScNewTemplateDefinition) <- Option(eb.getParent)
          } yield new ScUAnonymousClass(nt, eb, _: LazyUElement)).orNull

        case e: ScNewTemplateDefinition if e.extendsBlock.isAnonymousClass =>
          new ScUObjectLiteralExpression(e, _)

        case constructorInvocation: ScConstructorInvocation =>
          val isAnnotationConstructorCall = constructorInvocation.getParent.is[ScAnnotationExpr]
          if (isAnnotationConstructorCall)
            null
          else
            new ScUConstructorCallExpression(constructorInvocation, _)

        case e: ScAnnotation =>
          new ScUAnnotation(e, _)

        // ========================= LAMBDAS ====================================

        case lambdaExpr: ScFunctionExpr =>
          new ScULambdaExpression(lambdaExpr, _)

        case block: ScBlock if block.isPartialFunction =>
          new ScUPartialLambdaExpression(block, _)

        case us: ScUnderscoreSection if us.bindingExpr.collect {
              case ScReference(_: PsiMethod) =>
            }.isDefined =>
          us.bindingExpr match {
            case Some(binding: ScReference) =>
              new ScUCallableReferenceExpression(binding, _)
            case _ => null
          }

        case us: ScExpression if isUnderscoreFunction(us) && convertLambdas =>
          new ScUUnderscoreLambdaExpression(us, _)

        case mv: ScExpression if isMethodValue(mv, convertLambdas) =>
          mv match {
            case ref: ScReference =>
              new ScUCallableReferenceExpression(ref, _)
            case _ => new ScUMethodValueLambdaExpression(mv, _)
          }

        // ========================= CONTROL STRUCTURES =========================

        case e: ScIf     => new ScUIfExpression(e, _)
        case e: ScWhile  => new ScUWhileExpression(e, _)
        case e: ScDo     => new ScUDoWhileExpression(e, _)
        case e: ScTry    => new ScUTryExpression(e, _)
        case e: ScThrow  => new ScUThrowExpression(e, _)
        case e: ScReturn => new ScUReturnExpression(e, _)
        case e: ScMatch  => new ScUSwitchExpression(e, _)
        case e: ScCaseClauses =>
          e.getParent match {
            case bl: ScBlock if bl.isInCatchBlock => null
            case _                                => new ScUCaseClausesList(e, _)
          }
        case e: ScCaseClause =>
          if (isInsideCatchBlock(e)) new ScUCatchExpression(e, _)
          else new ScUCaseClause(e, _)
        case e: ScBlock if e.getParent.is[ScCaseClause] =>
          e.getParent match {
            case clause: ScCaseClause if !isInsideCatchBlock(clause) =>
              new ScUCaseClauseBodyList(e, _)
            case _ => new ScUBlockExpression(e, _)
          }

        // ========================= EXPRESSION GROUPS ==========================

        case e: ScBlock             => new ScUBlockExpression(e, _)
        case e: ScInfixExpr         => new ScUBinaryExpression(e, _)
        case e: ScTypedExpression   => new ScUBinaryExpressionWithType(e, _)
        case e: ScPostfixExpr       => new ScUPostfixExpression(e, _)
        case e: ScPrefixExpr        => new ScUPrefixExpression(e, _)
        case e: ScParenthesisedExpr => new ScUParenthesizedExpression(e, _)
        case e: ScAssignment =>
          if (e.isNamedParameter) new ScUNamedExpression(e, _)
          else new ScUAssignment(e, _)

        // ======================== CALLS =======================================

        //region Special section that converts PSI call elements to
        // UAST call expressions if the required type is a call expression.
        // Otherwise PSI visitor won't be able to find some UAST calls
        // because they will be converted to UQualifiedExpression's
        case e: ScMethodCall if requiredType == classOf[UCallExpression] =>
          new ScUMethodCallExpression(e, _)

        case e: ScGenericCall
            if !e.getParent.is[ScMethodCall] &&
              requiredType == classOf[UCallExpression] =>
          new ScUGenericCallExpression(e, _)

        case funRef: ScReferenceExpression
            if !funRef.getParent.is[ScMethodCall, ScGenericCall] &&
              requiredType == classOf[UCallExpression] &&
              funRef.resolve().is[PsiMethod, ScSyntheticFunction] =>

          new ScUReferenceCallExpression(funRef, _)
        //endregion

        case e: ScNewTemplateDefinition =>
          //noinspection ScalaUnnecessaryParentheses
          e.firstConstructorInvocation
            .map(c => new ScUConstructorCallExpression(c, _: LazyUElement))
            .getOrElse(null)

        case e: ScMethodCall =>
          e.getInvokedExpr match {
            case ref: ScReferenceExpression if ref.isQualified =>
              new ScUQualifiedReferenceExpression(ref, sourcePsi = e, _)
            case gc: ScGenericCall =>
              gc.referencedExpr match {
                case ref: ScReferenceExpression if ref.isQualified =>
                  new ScUQualifiedReferenceExpression(ref, sourcePsi = e, _)
                case _ =>
                  new ScUMethodCallExpression(e, _)
              }
            case _ => new ScUMethodCallExpression(e, _)
          }

        case e: ScGenericCall if !e.getParent.is[ScMethodCall] =>
          e.referencedExpr match {
            case ref: ScReferenceExpression if ref.isQualified =>
              new ScUQualifiedReferenceExpression(ref, sourcePsi = e, _)
            case _ =>
              new ScUGenericCallExpression(e, _)
          }

        // ========================= REFERENCES =================================

        case e: ScReferenceExpression if e.isQualified =>
          e.getParent match {
            case mc: ScMethodCall =>
              new ScUQualifiedReferenceExpression(e, sourcePsi = mc, _)
            case gc: ScGenericCall =>
              gc.getParent match {
                case mc: ScMethodCall =>
                  new ScUQualifiedReferenceExpression(e, sourcePsi = mc, _)
                case _ =>
                  new ScUQualifiedReferenceExpression(e, sourcePsi = gc, _)
              }
            case _ =>
              new ScUQualifiedReferenceExpression(e, _)
          }

        case funRef: ScReferenceExpression
          if !funRef.getParent.is[ScMethodCall, ScGenericCall, ScUnderscoreSection] &&
            funRef.resolve().is[PsiMethod, ScSyntheticFunction] =>
          new ScUReferenceCallExpression(funRef, _)

        case ScUReferenceExpression(parent2ScURef) =>
          parent2ScURef(_)

        case e: ScTypeElement if e.getFirstChild.is[ScReference] =>
          new ScUTypeReferenceExpression(
            e.getFirstChild.asInstanceOf[ScReference],
            typeProvider = Some(e),
            sourcePsi = e,
            _
          )
        case e: ScThisReference  => new ScUThisExpression(e, _)
        case e: ScSuperReference => new ScUSuperExpression(e, _)

        // ========================= LEAF ELEMENTS ==============================

        case e: ScLiteral => ScULiteral(e, _)
        case e: ScUnderscoreSection if e.bindingExpr.isEmpty =>
          new ScUUnderscoreExpression(e, _)

        /**
          * Some 3-rd party UAST based inspections try to
          * convert [[LeafPsiElement]] representing identifier to [[UIdentifier]]
          */
        case e: LeafPsiElement
            if e.getElementType == ScalaTokenTypes.tIDENTIFIER =>
          (_: LazyUElement) =>
            new LazyAnyUParentUIdentifier(e)

        // ========================= UNSUPPORTED ================================

        case e: ScFor => new ScUEmptyExpressionWithGivenType(e, _)
        case _        => null
      }): LazyUElement => UElement)
        .map(Free.fromLazyConstructor[UElement](_))

    elementOpt.flatMap { element =>
      element.standalone match {
        case standalone: U =>

          if (isUnitTestMode && !possibleSourceTypesCheckIsActive && !isPossibleToConvert(requiredType, sourcePsi)) {
            throw new AssertionError(s"${requiredType.getName} is not expected from ${sourcePsi.getClass.getName}, got ${standalone.getClass.getName}")
          }

          Some(element.asInstanceOf[Free[U]])
        case _ => None
      }
    }
  }

  private def isMethodValue(expr: ScExpression, convertLambdas: Boolean): Boolean = {
    convertLambdas && MethodValue.unapply(expr).isDefined
  }

  private def firstConvertibleParent(element: PsiElement): Option[UElement] =
    element.parents.flatMap(convertWithParent).headOption

  private def makeUParent(sourcePsi: PsiElement,
                          free: Free[_ <: UElement]): Option[UElement] = {

    val detachedUElement = free.standalone
    val firstPossibleParent = firstConvertibleParent(sourcePsi)

    doParentClarification(sourcePsi, detachedUElement, firstPossibleParent)
  }

  def doParentClarification(
    source: PsiElement,
    element: UElement,
    estimatedParent: Option[UElement]
  ): Option[UElement] = {

    val tuple = (source, element, estimatedParent.orNull)
    val clarifiedParent =
      tuple match {
        // skips reference for methods leaving UIdentifier only
        // because otherwise they would have the same source psi
        case (_, _: UIdentifier, uParent: USimpleNameReferenceExpression) =>
          uParent.getUastParent match {
            case mc: UCallExpression => mc
            case _                   => uParent
          }

        // selects method call as a parent for method identifier
        case (_, _: UIdentifier, uParent: UQualifiedReferenceExpression) =>
          uParent.getSelector

        // if the expression is functional its parent is
        // lambda of this expression
        case (e: ScExpression, uElement, _)
            if !uElement.is[UCallableReferenceExpression] &&
              !uElement.is[ULambdaExpression] &&
              SAMUtil.isFunctionalExpression(e) =>
          convertWithParentTo[ULambdaExpression](e).orNull

        // selects method call as a parent for method arguments
        case (arg, _: UExpression, uParent: UQualifiedReferenceExpression)
            if arg.getParent.is[ScArgumentExprList] =>
          uParent.getSelector

        // skips block for call expressions with braced arguments
        case (_, _: ULambdaExpression, uParent: UBlockExpression) =>
          uParent.getUastParent match {
            case call: UCallExpression => call
            case _                     => uParent
          }

        // skips catch block as a parent for catch clause
        case (_, _: UCatchClause, uParent: UBlockExpression) =>
          uParent.getUastParent

        /** Wraps unnamed annotation args into [[ScUUnnamedExpression]] */
        case (scExpr: ScExpression, uElement, uParent: UAnnotation)
            if !uElement.is[UNamedExpression] =>
          new ScUUnnamedExpression(scExpr, LazyUElement.just(uParent))

        // skips constructor invocation as an ancestor inside annotation
        case (_, _, constructorCall: UCallExpression)
            if constructorCall.getUastParent.is[UAnnotation] =>
          constructorCall.getUastParent

        // skips type reference as an ancestor inside constructor invocation
        case (_, _, typeRef: UTypeReferenceExpression)
            if typeRef.getUastParent.is[UCallExpression] =>
          typeRef.getUastParent

        // skips type reference as an ancestor inside annotation
        case (_, _, typeRef: UTypeReferenceExpression)
            if typeRef.getUastParent.is[UAnnotation] =>
          typeRef.getUastParent

        // skips primary constructor as a parent for the "field"
        case (_, _: UField, primaryConstructor: UMethod) =>
          primaryConstructor.getUastParent

        /**
          * As a parent for variable declaration returns
          *  - containing class for "fields" skipping declaration expr
          *  - containing declaration expr for local variables
          */
        case (_: ScReferencePattern, _, declsExpr: UDeclarationsExpression) =>
          declsExpr.getUastParent match {
            case cls: UClass => cls
            case _           => declsExpr
          }

        /**
          * As a parent for initializer expression returns __first__ variable
          * inside Scala multiple variable declaration list, e.g.
          * `a` in `val a, b, c = 1`.
          * Applies for both "fields" and local variables.
          *
          * There is a special case for local functions
          * @see [[ScULocalFunctionDeclarationExpression]]
          */
        case (_, _, declsExpr: UDeclarationsExpression) =>
          val decls = declsExpr.getDeclarations
          val first = if (!decls.isEmpty) decls.get(0) else null
          first match {
            case uVar: UVariable =>
              uVar.getSourcePsi match {
                case fun: ScFunctionDefinition if fun.isLocal =>
                  uVar.getUastInitializer
                case _ => uVar
              }
            case _ => declsExpr
          }

        // skips parent when PSI parent converts to the same physical UAST element
        case (_, uElement, uParent)
            if uElement == uParent && uElement.getSourcePsi != null =>
          uParent.getUastParent

        case _ =>
          estimatedParent.orNull
      }

    val result = Option(clarifiedParent).map { parent =>
      (source, element, parent) match {

        /** Wraps last statement in function in [[ScUImplicitReturnExpression]] */
        case (source, uExpr: UExpression, uParent)
            if isExplicitFunctionLastStatementWithoutReturn(source) ||
              (!uExpr.is[ULambdaExpression] &&
                isImplicitFunctionLastStatementWithoutReturn(source)) =>
          uParent match {
            case _: UBlockExpression =>
              ScUImplicitReturnExpression
                .convertAndWrapIntoReturn(source, LazyUElement.just(uParent))
            case _ =>
              ScUImplicitBlockExpression
                .convertAndWrapIntoBlock(source, LazyUElement.just(uParent))
                .getExpressions
                .asScala
                .headOption
                .orNull
          }

        /** Wraps function expression bodies in [[ScUImplicitBlockExpression]] */
        case (
            source,
            uExpr: UExpression,
            uParent @ (_: UMethod | _: ULambdaExpression)
            ) if !uExpr.is[UBlockExpression] =>
          ScUImplicitBlockExpression
            .convertAndWrapIntoBlock(source, LazyUElement.just(uParent))

        case _ => parent
      }
    }
    result
  }

  //todo: is there a better way to restrict possible conversions?
  private def isPossibleToConvert[U <: UElement](uastRequiredType: Class[U], scalaElement: PsiElement): Boolean = {
    def additionalIdentifierCheck: Boolean = scalaElement match {
      case e: LeafPsiElement => e.getElementType == ScalaTokenTypes.tIDENTIFIER
      case _ => false
    }

    def extraFieldCheck: Boolean = scalaElement match {
      case namePattern: ScReferencePattern =>
        Option(getParentOfType(namePattern, classOf[ScValueOrVariable])).exists(!_.isLocal)
      case classParam: ScClassParameter =>
        classParam.isClassMember
      case _ => true
    }

    def extraExpressionCheck: Boolean = scalaElement match {
      case te: ScTypeElement => te.getFirstChild.is[ScReference]

      //ScULocalFunctionDeclarationExpression
      case e: ScFunctionDefinition => e.isLocal

      case _ => true
    }

    //avoid converting everything to UAST when a particular type is expected
    ScalaUastSourceTypeMapping.canConvert(scalaElement, Array(uastRequiredType)) && (
      if      (uastRequiredType == classOf[UIdentifier]) additionalIdentifierCheck
      else if (uastRequiredType == classOf[UField]) extraFieldCheck
      else if (uastRequiredType == classOf[UExpression]) extraExpressionCheck
      else true
    )
  }

  private class LazyAnyUParentUIdentifier(@Nullable sourcePsi: PsiElement)
      extends UIdentifier(sourcePsi, null) {

    @Nullable
    override lazy val getUastParent: UElement =
      Option(sourcePsi)
        .flatMap(e => makeUParent(e, Free.ignoringParent[UElement](this)))
        .orNull

    override def toString: String = asLogString()
  }

  private object ConverterUtils {
    //noinspection ScalaUnusedSymbol
    def isInsideCatchBlock(c: ScCaseClause): Boolean =
      (for {
        cc @ (_x: ScCaseClauses) <- Option(c.getParent)
        bl @ (_x: ScBlock) <- Option(cc.getParent)
        if bl.isInCatchBlock
      } yield 42).isDefined
  }
}
