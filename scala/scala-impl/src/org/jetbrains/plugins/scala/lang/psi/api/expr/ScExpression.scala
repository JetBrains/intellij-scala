package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import org.jetbrains.plugins.scala.{NlsString, ScalaBundle}
import org.jetbrains.plugins.scala.caches.cachedWithRecursionGuard
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{MethodValue, isAnonymousExpression}
import org.jetbrains.plugins.scala.lang.psi.api.{ImplicitArgumentsOwner, InferUtil, ScalaPsiElement, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.SafeCheckException
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScIntegerLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ExpectedTypes.ParameterType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible
import org.jetbrains.plugins.scala.lang.psi.light.LightContextFunctionParameter
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.{Scala_2_11, Scala_2_13}
import org.jetbrains.plugins.scala.util.SAMUtil

import scala.annotation.tailrec

trait ScExpression extends ScBlockStatement
  with PsiAnnotationMemberValue
  with ImplicitArgumentsOwner
  with Typeable with Compatibility.Expression {

  import ScExpression._

  override def `type`(): TypeResult =
    this.getTypeAfterImplicitConversion().tr

  override protected def updateImplicitArguments(): Unit = {
    if (ScUnderScoreSectionUtil.isUnderscoreFunction(this))
      this.getTypeWithoutImplicits(fromUnderscore = true)
    else
      `type`()
  }

  protected def innerType: TypeResult =
    Failure(ScalaBundle.message("no.type.inferred", getText))

  /**
    * Some expression may be replaced only with another one
    */
  def replaceExpression(expr: ScExpression, removeParenthesis: Boolean): ScExpression = {
    val oldParent = getParent
    if (oldParent == null) throw new PsiInvalidElementAccessException(this)
    if (removeParenthesis && oldParent.is[ScParenthesisedExpr]) {
      return oldParent.asInstanceOf[ScExpression].replaceExpression(expr, removeParenthesis = true)
    }
    val newExpr = if (ScalaPsiUtil.needParentheses(this, expr)) {
      ScalaPsiElementFactory.createExpressionFromText(expr.getText.parenthesize(), expr)
    } else expr
    val parentNode = oldParent.getNode
    val newNode = newExpr.copy.getNode
    parentNode.replaceChild(this.getNode, newNode)
    newNode.getPsi.asInstanceOf[ScExpression]
  }

  @volatile
  private var additionalExpression: Option[(ScExpression, ScType)] = None

  def setAdditionalExpression(additionalExpression: Option[(ScExpression, ScType)]): Unit = {
    this.additionalExpression = additionalExpression
  }

  /**
    * This method should be used to get implicit conversions and used imports, while eta expanded method return was
    * implicitly converted
    *
    * @return mirror for this expression, in case if it exists
    */
  def getAdditionalExpression: Option[(ScExpression, ScType)] = {
    `type`()
    additionalExpression
  }

  def implicitElement(fromUnderscore: Boolean = false,
                      expectedOption: => Option[ScType] = this.smartExpectedType()): Option[PsiNamedElement] = {
    implicitConversion(fromUnderscore, expectedOption).map(_.element)
  }

  def implicitConversion(fromUnderscore: Boolean = false,
                         expectedOption: => Option[ScType] = this.smartExpectedType()): Option[ScalaResolveResult] = {

    def conversionForReference(reference: ScReferenceExpression) = reference.multiResolveScala(false) match {
      case Array(result) => result.implicitConversion
      case _ => None
    }

    def inner(element: ScalaPsiElement): Option[ScalaResolveResult] = element.getParent match {
      case reference: ScReferenceExpression =>
        conversionForReference(reference)
      case infix: ScInfixExpr if this == infix.getBaseExpr =>
        conversionForReference(infix.operation)
      case call: ScMethodCall => call.getImplicitFunction
      case generator: ScGenerator =>
        generator.desugared.flatMap { _.generatorExpr }.flatMap { _.implicitConversion(expectedOption = expectedOption) }
      case _: ScParenthesisedExpr => None
      case _ =>
        this.getTypeAfterImplicitConversion(expectedOption = expectedOption, fromUnderscore = fromUnderscore).implicitConversion
    }

    inner(this)
  }

  override def getTypeAfterImplicitConversion(
    checkImplicits: Boolean = true,
    isShape: Boolean = false,
    expectedOption: Option[ScType] = None,
    ignoreBaseTypes: Boolean = false,
    fromUnderscore: Boolean = false
  ): ExpressionTypeResult =
    cachedWithRecursionGuard(
      "ScExpression.getTypeAfterImplicitConversion",
      this,
      ExpressionTypeResult(Failure(NlsString.force("Recursive getTypeAfterImplicitConversion"))),
      BlockModificationTracker(this),
      (checkImplicits, isShape, expectedOption, ignoreBaseTypes, fromUnderscore)
    ) {
      def isJavaReflectPolymorphic =
        this.scalaLanguageLevelOrDefault >= Scala_2_11 &&
          ScalaPsiUtil.isJavaReflectPolymorphicSignature(this)

      if (isShape) ExpressionTypeResult(Right(shape(this).getOrElse(Nothing)))
      else {
        val expected = expectedOption.orElse(this.expectedType(fromUnderscore = fromUnderscore))
        val tr = this.getTypeWithoutImplicits(ignoreBaseTypes, fromUnderscore)

        (expected, tr.toOption) match {
          case (Some(expType), Some(tp))
            if checkImplicits && !tp.conforms(expType) => //do not try implicit conversions for shape check or already correct type

            // isSAMEnabled is checked in tryAdaptTypeToSAM, but we can cut it right here
            val adapted = if (this.isSAMEnabled) this.tryAdaptTypeToSAM(tp, expType, fromUnderscore) else None
            adapted.getOrElse(
              if (isJavaReflectPolymorphic) ExpressionTypeResult(Right(expType))
              else this.updateTypeWithImplicitConversion(tp, expType)
            )
          case _ => ExpressionTypeResult(tr)
        }
      }
    }
}

object ScExpression {
  final case class ExpressionTypeResult(
    tr:                 TypeResult,
    importsUsed:        Set[ImportUsed] = Set.empty,
    implicitConversion: Option[ScalaResolveResult] = None
  ) {
    def implicitFunction: Option[PsiNamedElement] = implicitConversion.map(_.element)
  }

  object Type {
    def unapply(exp: ScExpression): Option[ScType] = exp.`type`().toOption
  }

  implicit class Ext(private val expr: ScExpression) extends AnyVal {
    private implicit def elementScope: ElementScope = expr.elementScope

    private def project = elementScope.projectContext

    def contextFunctionParameters: Seq[LightContextFunctionParameter] =
      expr match {
        case fun: ScFunctionExpr if fun.isContext => Seq.empty
        case _ =>
          expectedType().toSeq.flatMap {
            case p: ParameterizedType => p.contextParameters
            case _                    => Seq.empty
          }
      }

    def expectedType(fromUnderscore: Boolean = true): Option[ScType] =
      expectedTypeEx(fromUnderscore).map(_._1)

    def expectedTypeEx(fromUnderscore: Boolean = true): Option[ParameterType] =
      ExpectedTypes.instance().expectedExprType(expr, fromUnderscore)

    def expectedTypes(fromUnderscore: Boolean = true): Seq[ScType] = expectedTypesEx(fromUnderscore).map(_._1).toSeq

    def expectedTypesEx(fromUnderscore: Boolean = true): Array[ParameterType] = cachedWithRecursionGuard("expectedTypesEx", expr, Array.empty[ParameterType], BlockModificationTracker(expr), Tuple1(fromUnderscore)) {
      ExpectedTypes.instance().expectedExprTypes(expr, fromUnderscore = fromUnderscore)
    }

    def smartExpectedType(fromUnderscore: Boolean = true): Option[ScType] = cachedWithRecursionGuard("smartExpectedType", expr, Option.empty[ScType], BlockModificationTracker(expr), Tuple1(fromUnderscore)) {
      ExpectedTypes.instance().smartExpectedType(expr, fromUnderscore)
    }

    def getTypeIgnoreBaseType: TypeResult = expr.getTypeAfterImplicitConversion(ignoreBaseTypes = true).tr

    def getNonValueType(ignoreBaseType: Boolean = false,
                        fromUnderscore: Boolean = false): TypeResult = cachedWithRecursionGuard("getNonValueType", expr, Failure(NlsString.force("Recursive getNonValueType")), BlockModificationTracker(expr), (ignoreBaseType, fromUnderscore)) {
      ProgressManager.checkCanceled()
      if (fromUnderscore) expr.innerType
      else {
        val unders = ScUnderScoreSectionUtil.underscores(expr)
        if (unders.isEmpty) expr.innerType
        else {
          val params = unders.zipWithIndex.map {
            case (u, index) =>
              val tpe = u.getNonValueType(ignoreBaseType).getOrAny.inferValueType.unpackedType
              Parameter(tpe, isRepeated = false, index = index)
          }
          val methType =
            ScMethodType(
              expr
                .getTypeAfterImplicitConversion(
                  ignoreBaseTypes = ignoreBaseType,
                  fromUnderscore  = true
                )
                .tr
                .getOrAny,
              params,
              isImplicit = false
            )
          Right(methType)
        }
      }
    }

    def getTypeWithoutImplicits(
      ignoreBaseType: Boolean = false,
      fromUnderscore: Boolean = false
    ): TypeResult = cachedWithRecursionGuard("getTypeWithoutImplicits", expr, Failure(NlsString.force("Recursive getTypeWithoutImplicits")), BlockModificationTracker(expr), (ignoreBaseType, fromUnderscore)) {
      ProgressManager.checkCanceled()

      expr match {
        case literals.ScNullLiteral(typeWithoutImplicits) => Right(typeWithoutImplicits)
        case _ =>
          val maybeNonValueType = expr.getNonValueType(ignoreBaseType, fromUnderscore)
          maybeNonValueType.flatMap { nonValueType =>
            val expectedType = this.expectedType(fromUnderscore = fromUnderscore)
            val widened      = nonValueType.widenLiteralType(expr, expectedType)
            val maybeSAMpt   = expectedType.flatMap(widened.expectedSAMType(expr, fromUnderscore, _))

            def inferValueTypeRetractingNothing(tpe: ScType): ScType = tpe match {
              case tpt @ ScTypePolymorphicType(internalType, _) =>
                val subst = tpt.polymorphicTypeSubstitutor

                internalType.inferValueType.recursiveVarianceUpdate() {
                  case (tpt: TypeParameterType, variance) =>

                    /**
                     * See `adjustTypeArgs` in scalac, if type parameter of a polymorphic method
                     * is inferred to scala.Nothing and it is not covariant in `internalType`, it is
                     * considered to be undetermined.
                     */
                    val substed = subst(tpt)
                    val retractNothing = substed.isNothing && !variance.isPositive

                    val result =
                      if (retractNothing) ScAbstractType(tpt.typeParameter, tpt.lowerType, tpt.upperType)
                      else                substed

                    ReplaceWith(result)
                  case _ => ProcessSubtypes
                }
              case _ => tpe.inferValueType
            }

            def inferValueType(tpe: ScType): ScType =
              if (expr.is[ScPolyFunctionExpr])                 tpe
              else if (expr.getContext.is[ScArgumentExprList]) inferValueTypeRetractingNothing(tpe)
              else                                             tpe.inferValueType

            val valueType =
              inferValueType(
                widened
                  .dropMethodTypeEmptyParams(expr, expectedType)
                  .updateWithExpected(expr, maybeSAMpt.orElse(expectedType), fromUnderscore)
              ).synthesizeContextFunctionType(expectedType, expr)
               .unpackedType
               .synthesizePartialFunctionType(expr, expectedType)
               .untupleFunction(expr, expectedType)

            if (ignoreBaseType) Right(valueType)
            else
              expectedType match {
                case None                                                   => Right(valueType)
                case Some(expected) if expected.removeAbstracts.equiv(Unit) => Right(Unit) //value discarding
                case Some(expected)                                         => numericWideningOrNarrowing(valueType, expected)
              }
          }
      }
    }

    //has side effect!
    private[ScExpression] def updateWithImplicitParameters(tpe: ScType, checkExpectedType: Boolean, fromUnderscore: Boolean): ScType = {
      val (newType, params) = updatedWithImplicitParameters(tpe, checkExpectedType)

      if (ScUnderScoreSectionUtil.isUnderscoreFunction(expr) == fromUnderscore) {
        expr.setImplicitArguments(params)
      }

      newType
    }

    def updatedWithImplicitParameters(tpe: ScType, checkExpectedType: Boolean): (ScType, Option[Seq[ScalaResolveResult]]) = {
      val checkImplicitParameters = ScalaPsiUtil.withEtaExpansion(expr)
      if (checkImplicitParameters) {
        val (updatedType, implicits, _) =
          InferUtil.updateTypeWithImplicitParameters(tpe, expr, None, checkExpectedType, fullInfo = false)
        (updatedType, implicits)
      } else (tpe, None)
    }

    //numeric literal narrowing
    def isNarrowing(expected: ScType): Option[TypeResult] = {
      import expr.projectContext

      def isByte(v: Long) = v >= scala.Byte.MinValue && v <= scala.Byte.MaxValue

      def isChar(v: Long) = v >= scala.Char.MinValue && v <= scala.Char.MaxValue

      def isShort(v: Long) = v >= scala.Short.MinValue && v <= scala.Short.MaxValue

      def success(t: ScType) = Some(Right(t))

      def findIntLiteralValue(expr: ScExpression): Option[Int] = expr match {
        case ScIntegerLiteral(value) => Some(value)
        case ScPrefixExpr(op, operand) if Set("+", "-").contains(op.refName) =>
          findIntLiteralValue(operand).map(v => if (op.refName == "-") -v else v)
        case ScParenthesisedExpr(inner) => findIntLiteralValue(inner)
        case _ => None
      }


      val intLiteralValue: Int = findIntLiteralValue(expr) match {
        case Some(value) => value
        case _ => return None
      }

      val stdTypes = StdTypes.instance
      import stdTypes._

      expected.removeAbstracts.removeAliasDefinitions() match {
        case Char if isChar(intLiteralValue)   => success(Char)
        case Byte if isByte(intLiteralValue)   => success(Byte)
        case Short if isShort(intLiteralValue) => success(Short)
        case _                                 => None
      }
    }

    def implicitConversions(fromUnderscore: Boolean = false): Seq[PsiNamedElement] = {
      ScImplicitlyConvertible.implicits(expr, fromUnderscore)
        .sortWith {
          case (first, second) =>
            val firstName = first.name
            val secondName = second.name

            def isAnyTo(string: String): Boolean =
              string.matches("^[a|A]ny(2|To|to).+$")

            isAnyTo(secondName) match {
              case isSecondAnyTo if isAnyTo(firstName) ^ isSecondAnyTo => isSecondAnyTo
              case _ => firstName.compareTo(secondName) < 0
            }
        }
    }

    //numeric widening
    private def isWidening(valueType: ScType, expected: ScType): Option[TypeResult] = {
      val (l, r) = (getStdType(valueType), getStdType(expected)) match {
        case (Some(left), Some(right)) => (left, right)
        case _                         => return None
      }

      val stdTypes = project.stdTypes
      import stdTypes._

      (l, r) match {
        case (Byte, Short | Int | Long | Float | Double)        => Some(Right(expected))
        case (Short, Int | Long | Float | Double)               => Some(Right(expected))
        case (Char, Byte | Short | Int | Long | Float | Double) => Some(Right(expected))
        case (Int, Long | Float | Double)                       => Some(Right(expected))
        case (Long, Float | Double)                             => Some(Right(expected))
        case (Float, Double)                                    => Some(Right(expected))
        case _                                                  => None
      }
    }

    private def numericWideningOrNarrowing(valType: ScType, expected: ScType): TypeResult = {
      val narrowing = isNarrowing(expected)
      if (narrowing.isDefined) narrowing.get
      else {
        val widening = isWidening(valType, expected)
        if (widening.isDefined) widening.get
        else Right(valType)
      }
    }

    private def getStdType(t: ScType): Option[StdType] = {
      val stdTypes  = project.stdTypes
      val dealiased = t.widenIfLiteral.removeAliasDefinitions()
      import stdTypes._

      dealiased match {
        case AnyVal                           => Some(AnyVal)
        case valType: ValType                 => Some(valType)
        case designatorType: ScDesignatorType => designatorType.getValType
        case _                                => None
      }
    }
  }

  @tailrec
  private def shouldUpdateImplicitParams(expr: ScExpression): Boolean = {
    //true if it wasn't updated in MethodInvocation method
    expr match {
      case _: ScPrefixExpr                    => true
      case _: ScPostfixExpr                   => true
      case _: ScPolyFunctionExpr              => false
      case ChildOf(ScInfixExpr(_, `expr`, _)) => false //implicit parameters are in infix expression
      case ChildOf(_: ScGenericCall)          => false //implicit parameters are in generic call
      case ChildOf(ScAssignment(`expr`, _))   => false //simple var cannot have implicit parameters, otherwise it's for assignment
      case _: MethodInvocation                => false
      case ScParenthesisedExpr(inner)         => shouldUpdateImplicitParams(inner)
      case fn: ScFunctionExpr if fn.isContext => false
      case _                                  => true
    }
  }

  private implicit class ExprTypeUpdates(private val scType: ScType) extends AnyVal {
    def widenLiteralType(expr: ScExpression, expectedType: Option[ScType]): ScType = {
      def isLiteralType(tp: ScType) = tp.removeAliasDefinitions().isInstanceOf[ScLiteralType]

      scType match {
        case lt: ScLiteralType if !expr.literalTypesEnabled && !expectedType.exists(isLiteralType) =>
          lt.wideType
        case _ => scType
      }
    }

    /**
     * if the expected type of an expression E is a context function type (T_1, ..., T_n) ?=> U and
     * E is not already a context function literal, E is converted to a context function literal by rewriting it to
     * (x_1: T1, ..., x_n: Tn) ?=> E
     */
    final def synthesizeContextFunctionType(pt: Option[ScType], expr: ScExpression)(implicit scope: ElementScope): ScType =
      scType match {
        case cft @ ContextFunctionType(_, _) => cft
        case _ =>
          pt.map(_.removeAliasDefinitions()) match {
            case Some(cft: ParameterizedType) if ContextFunctionType.isContextFunctionType(cft) =>
              //Trigger all usages by traversing ImplicitArgumentOwner children of this expr
              expr.acceptChildren(new ScalaRecursiveElementVisitor {
                override def visitScalaElement(element: ScalaPsiElement): Unit = {
                  element match {
                    case implicitOwner: ImplicitArgumentsOwner => implicitOwner.findImplicitArguments
                    case _                                     => ()
                  }

                  super.visitScalaElement(element)
                }
              })

              val actualParamTypes = expr.contextFunctionParameters.map(_.contextFunctionParameterType.getOrAny)
              ContextFunctionType((scType, actualParamTypes))
            case _ => scType
          }
    }

    /**
     * Adapt expression of type (T_1, ... T_n) => R1 to
     * the expected type of tupled unary function TupleN(U_1, ... U_n) => R2
     * provided forall n. U_n <: T_n and R1 <: R2
     */
    final def untupleFunction(
      expr: ScExpression,
      pt:   Option[ScType]
    ): ScType =
      if (expr.isInScala3Module && SAMUtil.isFunctionalExpression(expr))
        scType match {
          case FunctionType(resTpe, paramTypes) =>
            pt.map(_.removeAliasDefinitions()) match {
              case Some(FunctionType(ptRes, Seq(t @ TupleType(ptParams))))
                if resTpe.conforms(ptRes) && parameterTypesMatch(paramTypes, ptParams) =>
                implicit val scope: ElementScope = expr.elementScope
                FunctionType((resTpe, Seq(t)))
              case _ => scType
            }
          case _ => scType
        }
      else scType

    private def parameterTypesMatch(params: Seq[ScType], ptParams: Seq[ScType]): Boolean =
      ptParams.corresponds(params)(_.conforms(_))

    /**
     * https://github.com/scala/scala/pull/8172
     *
     * Adapt type of a function literal, if the expected type is
     * PartialFunction with matching type arguments.
     */
    final def synthesizePartialFunctionType(
      expr:        ScExpression,
      expectedTpe: Option[ScType]
    ): ScType = {
      implicit val scope: ElementScope = expr.elementScope

      def flattenParamTypes(t: ScType): Seq[ScType] = t match {
        case TupleType(comps) => comps
        case _                => Seq(t)
      }

      def checkExpectedPartialFunctionType(
        resTpe:    ScType,
        paramTpes: Seq[ScType]
      ): ScType = expectedTpe match {
        case Some(PartialFunctionType(ptRes, ptParams))
            if resTpe.conforms(ptRes) && parameterTypesMatch(paramTpes, flattenParamTypes(ptParams)) &&
              expr.scalaLanguageLevelOrDefault >= Scala_2_13 =>
          val partialFunctionParamType =
            if (paramTpes.size == 1) paramTpes.head
            else TupleType(paramTpes)

          PartialFunctionType((resTpe, partialFunctionParamType))
        case _ => scType
      }

      expr match {
        case _: ScFunctionExpr => scType match {
          case FunctionType(rTpe, pTpes) => checkExpectedPartialFunctionType(rTpe, pTpes)
          case _                         => scType
        }
        case _ if ScUnderScoreSectionUtil.isUnderscoreFunction(expr) => scType match {
          case FunctionType(rTpe, pTpes) => checkExpectedPartialFunctionType(rTpe, pTpes)
          case _                         => scType
        }
        case _ => scType
      }
    }

    def expectedSAMType(
      expr:           ScExpression,
      fromUnderscore: Boolean,
      expected:       ScType
    ): Option[ScType] = {
      @scala.annotation.tailrec
      def checkForSAM(tp: ScType): Option[ScType] =
        tp match {
          case FunctionType(_, _)           => SAMUtil.toSAMType(expected, expr)
          case _: ScMethodType              => SAMUtil.toSAMType(expected, expr)
          case ScTypePolymorphicType(tp, _) => checkForSAM(tp)
          case _                            => None
        }

      def isTrivialSAM: Boolean = expected match {
        case FunctionType(_, _)        => true
        case ContextFunctionType(_, _) => true
        case _                         => false
      }

      if (!expr.isSAMEnabled) None
      else if (isTrivialSAM) None
      else expr match {
        case ScFunctionExpr(_, _) if fromUnderscore                      => checkForSAM(scType)
        case _ if !fromUnderscore && ScalaPsiUtil.isAnonExpression(expr) => checkForSAM(scType)
        case MethodValue(_)                                              => checkForSAM(scType)
        case _                                                           => None
      }
    }

    private def shouldApplyContextParameters(pt: ScType): Boolean =
      (scType, pt) match {
        case (ContextFunctionType(_, _), ContextFunctionType(_, _)) => false
        case _                                                      => true
      }

    def updateWithExpected(expr: ScExpression, expectedType: Option[ScType], fromUnderscore: Boolean): ScType =
      if (shouldUpdateImplicitParams(expr) && expectedType.forall(shouldApplyContextParameters)) {
        try {
          val updatedWithExpected =
            InferUtil.updateAccordingToExpectedType(
              scType,
              filterTypeParams = false,
              expectedType     = expectedType,
              expr             = expr,
              canThrowSCE      = true
            )

          expr.updateWithImplicitParameters(
            updatedWithExpected,
            checkExpectedType = true,
            fromUnderscore
          )
        } catch {
          case _: SafeCheckException =>
            expr.updateWithImplicitParameters(scType, checkExpectedType = false, fromUnderscore)
        }
      } else scType

    def dropMethodTypeEmptyParams(expr: ScExpression, expectedType: Option[ScType]): ScType = {
      val (retType, typeParams) = scType match {
        case ScTypePolymorphicType(ScMethodType(rt, params, _), tps) if params.isEmpty => (rt, Some(tps))
        case ScMethodType(rt, params, _) if params.isEmpty                             => (rt, None)
        case _                                                                         => return scType
      }

      val functionLikeType = FunctionLikeType(expr)
      val scalaVersion = expr.scalaLanguageLevelOrDefault
      import FunctionTypeMarker.SAM

      val shouldDrop =
        !ScUnderScoreSectionUtil.isUnderscore(expr) &&
          expectedType
            .map(_.removeAbstracts)
            .forall {
              case functionLikeType(marker, _, ptpes) => marker match {
                case SAM(_) => !(scalaVersion == Scala_2_11 && expr.isSAMEnabled)
                case _      => scalaVersion >= Scala_2_13 && ptpes.nonEmpty
              }
              case _ => true
            }

      if (!shouldDrop) scType
      else             typeParams.map(ScTypePolymorphicType(retType, _)).getOrElse(retType)
    }
  }

  private def shape(expression: ScExpression, ignoreAssign: Boolean = false): Option[ScType] = {
    import expression.projectContext

    def shapeIgnoringAssign(maybeExpression: Option[ScExpression]) = maybeExpression.flatMap {
      shape(_, ignoreAssign = true)
    }

    expression match {
      case assign: ScAssignment if !ignoreAssign && assign.referenceName.isDefined =>
        shapeIgnoringAssign(assign.rightExpression)
      case _ =>
        val arityAndResultType = Option(isAnonymousExpression(expression)).filter {
          case (-1, _) => false
          case _ => true
        }.map {
          case (i, expr: ScFunctionExpr) => (i, shapeIgnoringAssign(expr.result))
          case (i, _) => (i, None)
        }

        arityAndResultType.map {
          case (i, tp) => (Seq.fill(i)(Any), tp)
        }.map {
          case (argumentsTypes, maybeResultType) =>
            FunctionType(maybeResultType.getOrElse(Nothing), argumentsTypes)(expression.elementScope)
        }
    }
  }
}

object ExpectedType {
  def unapply(e: ScExpression): Option[ScType] = e.expectedType()
}

object NonValueType {
  def unapply(e: ScExpression): Option[ScType] = e.getNonValueType().toOption
}
