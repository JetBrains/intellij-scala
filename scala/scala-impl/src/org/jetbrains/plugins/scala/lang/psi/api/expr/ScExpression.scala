package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{MethodValue, isAnonymousExpression}
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.SafeCheckException
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScIntegerLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ExpectedTypes.ParameterType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.impl
import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.macroAnnotations.{CachedWithRecursionGuard, ModCount}
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.{Scala_2_11, Scala_2_13}
import org.jetbrains.plugins.scala.util.SAMUtil

import scala.annotation.tailrec

/**
  * @author ilyas, Alexander Podkhalyuzin
  */
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
    if (removeParenthesis && oldParent.isInstanceOf[ScParenthesisedExpr]) {
      return oldParent.asInstanceOf[ScExpression].replaceExpression(expr, removeParenthesis = true)
    }
    val newExpr = if (ScalaPsiUtil.needParentheses(this, expr)) {
      impl.ScalaPsiElementFactory.createExpressionFromText(expr.getText.parenthesize())
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

  @CachedWithRecursionGuard(
    this,
    ExpressionTypeResult(Failure("Recursive getTypeAfterImplicitConversion")),
    ModCount.getBlockModificationCount
  )
  override def getTypeAfterImplicitConversion(
    checkImplicits:  Boolean        = true,
    isShape:         Boolean        = false,
    expectedOption:  Option[ScType] = None,
    ignoreBaseTypes: Boolean        = false,
    fromUnderscore:  Boolean        = false
  ): ExpressionTypeResult = {
    def isJavaReflectPolymorphic =
      this.scalaLanguageLevelOrDefault >= Scala_2_11 &&
        ScalaPsiUtil.isJavaReflectPolymorphicSignature(this)

    if (isShape) ExpressionTypeResult(Right(shape(this).getOrElse(Nothing)))
    else {
      val expected = expectedOption.orElse(this.expectedType(fromUnderscore = fromUnderscore))
      val tr       = this.getTypeWithoutImplicits(ignoreBaseTypes, fromUnderscore)

      (expected, tr.toOption) match {
        case (Some(expType), Some(tp))
          if checkImplicits && !tp.conforms(expType) => //do not try implicit conversions for shape check or already correct type

          this.tryAdaptTypeToSAM(tp, expType, fromUnderscore).getOrElse(
            if (isJavaReflectPolymorphic) ExpressionTypeResult(Right(expType))
            else                          this.updateTypeWithImplicitConversion(tp, expType)
          )
        case _ => ExpressionTypeResult(tr)
      }
    }
  }
}

object ScExpression {
  final case class ExpressionTypeResult(
    tr:                 TypeResult,
    importsUsed:        collection.Set[ImportUsed] = Set.empty,
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

    def expectedType(fromUnderscore: Boolean = true): Option[ScType] =
      expectedTypeEx(fromUnderscore).map(_._1)

    def expectedTypeEx(fromUnderscore: Boolean = true): Option[ParameterType] =
      ExpectedTypes.instance().expectedExprType(expr, fromUnderscore)

    def expectedTypes(fromUnderscore: Boolean = true): Seq[ScType] = expectedTypesEx(fromUnderscore).map(_._1)

    @CachedWithRecursionGuard(expr, Array.empty[ParameterType], ModCount.getBlockModificationCount)
    def expectedTypesEx(fromUnderscore: Boolean = true): Array[ParameterType] = {
      ExpectedTypes.instance().expectedExprTypes(expr, fromUnderscore = fromUnderscore)
    }

    @CachedWithRecursionGuard(expr, None, ModCount.getBlockModificationCount)
    def smartExpectedType(fromUnderscore: Boolean = true): Option[ScType] = ExpectedTypes.instance().smartExpectedType(expr, fromUnderscore)

    def getTypeIgnoreBaseType: TypeResult = expr.getTypeAfterImplicitConversion(ignoreBaseTypes = true).tr

    @CachedWithRecursionGuard(expr, Failure("Recursive getNonValueType"), ModCount.getBlockModificationCount)
    def getNonValueType(ignoreBaseType: Boolean = false,
                        fromUnderscore: Boolean = false): TypeResult = {
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

    @CachedWithRecursionGuard(expr, Failure("Recursive getTypeWithoutImplicits"),
      ModCount.getBlockModificationCount)
    def getTypeWithoutImplicits(
      ignoreBaseType: Boolean = false,
      fromUnderscore: Boolean = false
    ): TypeResult = {
      ProgressManager.checkCanceled()

      expr match {
        case literals.ScNullLiteral(typeWithoutImplicits) => Right(typeWithoutImplicits)
        case _ =>
          expr.getNonValueType(ignoreBaseType, fromUnderscore).flatMap { nonValueType =>
            val expectedType = this.expectedType(fromUnderscore = fromUnderscore)
            val widened      = nonValueType.widenLiteralType(expr, expectedType)
            val maybeSAMpt   = expectedType.flatMap(widened.expectedSAMType(expr, fromUnderscore, _))

            val valueType =
              widened
                .dropMethodTypeEmptyParams(expr, expectedType)
                .updateWithExpected(expr, maybeSAMpt.orElse(expectedType), fromUnderscore)
                .inferValueType
                .unpackedType
                .synthesizePartialFunctionType(expr, expectedType)

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
      if (checkImplicitParameters)
        InferUtil.updateTypeWithImplicitParameters(tpe, expr, None, checkExpectedType, fullInfo = false)
      else (tpe, None)
    }

    //numeric literal narrowing
    def isNarrowing(expected: ScType): Option[TypeResult] = {
      import expr.projectContext

      def isByte(v: Long) = v >= scala.Byte.MinValue && v <= scala.Byte.MaxValue

      def isChar(v: Long) = v >= scala.Char.MinValue && v <= scala.Char.MaxValue

      def isShort(v: Long) = v >= scala.Short.MinValue && v <= scala.Short.MaxValue

      def success(t: ScType) = Some(Right(t))

      val intLiteralValue: Int = expr match {
        case ScIntegerLiteral(value) => value
        case ScPrefixExpr(op, ScIntegerLiteral(value)) if Set("+", "-").contains(op.refName) =>
          val mult = if (op.refName == "-") -1 else 1
          mult * value
        case _ => return None
      }

      val stdTypes = StdTypes.instance
      import stdTypes._

      expected.removeAbstracts match {
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

    @tailrec
    private def getStdType(t: ScType): Option[StdType] = {
      val stdTypes = project.stdTypes
      import stdTypes._

      t match {
        case AnyVal                           => Some(AnyVal)
        case valType: ValType                 => Some(valType)
        case designatorType: ScDesignatorType => designatorType.getValType
        case lt: ScLiteralType                => getStdType(lt.wideType)
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
      case ChildOf(ScInfixExpr(_, `expr`, _)) => false //implicit parameters are in infix expression
      case ChildOf(_: ScGenericCall)          => false //implicit parameters are in generic call
      case ChildOf(ScAssignment(`expr`, _))   => false //simple var cannot have implicit parameters, otherwise it's for assignment
      case _: MethodInvocation                => false
      case ScParenthesisedExpr(inner)         => shouldUpdateImplicitParams(inner)
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

      def parameterTypesMatch(ptParams: ScType, paramTpes: Seq[ScType]): Boolean =
        paramTpes.corresponds(flattenParamTypes(ptParams))(_.conforms(_)) ||
          paramTpes.corresponds(Seq(ptParams))(_.conforms(_))

      def checkExpectedPartialFunctionType(
        resTpe:    ScType,
        paramTpes: Seq[ScType]
      ): ScType = expectedTpe match {
        case Some(PartialFunctionType(ptRes, ptParams))
            if resTpe.conforms(ptRes) && parameterTypesMatch(ptParams, paramTpes) &&
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

      if (!expr.isSAMEnabled) None
      else expr match {
        case ScFunctionExpr(_, _) if fromUnderscore                      => checkForSAM(scType)
        case _ if !fromUnderscore && ScalaPsiUtil.isAnonExpression(expr) => checkForSAM(scType)
        case MethodValue(_)                                              => checkForSAM(scType)
        case _                                                           => None
      }
    }

    def updateWithExpected(expr: ScExpression, expectedType: Option[ScType], fromUnderscore: Boolean): ScType =
      if (shouldUpdateImplicitParams(expr)) {
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
      import FunctionTypeMarker.SAM

      val shouldDrop =
        !ScUnderScoreSectionUtil.isUnderscore(expr) &&
          expectedType
            .map(_.removeAbstracts)
            .forall {
              case functionLikeType(marker, _, _) => marker match {
                case SAM(_) => expr.scalaLanguageLevelOrDefault != Scala_2_11
                case _      => false
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
