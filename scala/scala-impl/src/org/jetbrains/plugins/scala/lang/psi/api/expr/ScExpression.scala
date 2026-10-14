package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cachedWithRecursionGuard}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{MethodValue, isAnonymousExpression}
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.{ImplicitArgumentsClause, SafeCheckException}
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ExpectedTypes.ParameterType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.{ImplicitArgumentsOwner, InferUtil, ScalaPsiElement, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ExpectedTypePropagation
import org.jetbrains.plugins.scala.lang.psi.impl.{CompilerType, ScalaPsiElementFactory}
import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible
import org.jetbrains.plugins.scala.lang.psi.light.LightContextFunctionParameter
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.{Scala_2_11, Scala_2_13}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.SAMUtil
import org.jetbrains.plugins.scala.{NlsString, ScalaBundle, Tracing}

import scala.annotation.tailrec

trait ScExpression extends ScBlockStatement
  with PsiAnnotationMemberValue
  with ImplicitArgumentsOwner
  with Typeable with Compatibility.Expression {

  import ScExpression._

  override def `type`(expectedType: Option[ScType]): TypeResult =
    this.getTypeAfterImplicitConversion(expectedOption = expectedType).tr

  override protected def defaultExpectedType: Option[ScType] =
    this.expectedType(fromUnderscore = false)

  override protected def updateImplicitArguments(): Option[ScType] =
    if (ScUnderScoreSectionUtil.isUnderscoreFunction(this)) {
      val expectedType = this.expectedType(fromUnderscore = true)
      this.getTypeWithoutImplicits(fromUnderscore = true, expectedType = expectedType)
      expectedType
    } else {
      val expectedType = defaultExpectedType
      `type`(expectedType)
      expectedType
    }

  /**
   * The type of this expression, before it is adapted to the expected type.
   *
   * @param expectedType the expected type of this expression, or the expected type of its body,
   *                     if this expression is an anonymous function in placeholder syntax,
   *                     see [[ScExpression.Ext.getNonValueType]].
   *                     Subexpressions, whose expected types are derived from it, must be typed against those
   *                     (see [[org.jetbrains.plugins.scala.lang.psi.impl.expr.ExpectedTypePropagation]]),
   *                     rather than against the expected types computed from their contexts.
   */
  protected def innerType(expectedType: Option[ScType]): TypeResult =
    Failure(ScalaBundle.message("no.type.inferred", getText))

  def replaceExpression(expr: ScExpression, removeParenthesis: Boolean): ScExpression = {
    val parent = getParent
    if (parent == null) {
      throw new PsiInvalidElementAccessException(this)
    }

    parent match {
      case parExpr: ScParenthesisedExpr if removeParenthesis =>
        parExpr.replaceExpression(expr, removeParenthesis)
      case _ =>
        replaceExpression(expr)
    }
  }

  /**
   * Replace expression with another one.<br>
   * The method checks if the new code requires parentheses and inserts them if needed.
   * It's done to avoid potentially-invalid code
   *
   * ATTENTION:
   * The method makes a copy of `expr`.<br>
   * Any existing pointers to psi elements form children of `expr` won't point to the actual inserted elements
   */
  def replaceExpression(expr: ScExpression): ScExpression = {
    val parent = getParent
    if (parent == null) {
      throw new PsiInvalidElementAccessException(this)
    }

    val newExpr: PsiElement =
      if (ScalaPsiUtil.needParentheses(this, expr))
        ScalaPsiElementFactory.createExpressionFromText(expr.getText.parenthesize(), expr)
      else
        expr.copy

    val newNode = newExpr.getNode

    //This does more then low-level ASTNode.replaceChild
    //For example, it makes sure that if a formatting is required, it's invoked for the inserted node.
    //This can matter for example if we inline `val value = +5` into expression `1+value`
    //If we simply replace `value` with `+5` we will get an invalid expression `1++5`
    //However, `CodeEditUtil.replaceChild` ensures to add extra space and make it `1+ +5`
    CodeEditUtil.replaceChild(parent.getNode, this.getNode, newNode)

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
        this.getTypeAdaptedTo(
          adaptTo        = expectedOption,
          checkImplicits = true,
          isShape        = false,
          expectedType   = this.expectedType(fromUnderscore),
          fromUnderscore = fromUnderscore
        ).implicitConversion
    }

    inner(this)
  }

  /**
   * @param expectedOption the expected type of this expression, or the expected type of its body,
   *                       if `fromUnderscore` is set, see [[ScExpression.Ext.getNonValueType]].
   *                       The default value is only correct when `fromUnderscore` is not set.
   */
  override def getTypeAfterImplicitConversion(
    checkImplicits:  Boolean        = true,
    isShape:         Boolean        = false,
    expectedOption:  Option[ScType] = defaultExpectedType,
    ignoreBaseTypes: Boolean        = false,
    fromUnderscore:  Boolean        = false
  ): ExpressionTypeResult =
    getTypeAdaptedTo(expectedOption, checkImplicits, isShape, expectedOption, ignoreBaseTypes, fromUnderscore)

  /**
   * Like [[getTypeAfterImplicitConversion]], but the expression is typed against `expectedType`,
   * while it is adapted (by SAM conversion, implicit conversion, etc.) to `adaptTo`.
   *
   * During overloading resolution an argument is typed against the expected type derived from the parameter types
   * of all the alternatives, but it is adapted to the parameter type of the alternative being checked,
   * see [[org.jetbrains.plugins.scala.lang.psi.types.Compatibility.checkMethodApplicability]].
   */
  def getTypeAdaptedTo(
    adaptTo:         Option[ScType],
    checkImplicits:  Boolean,
    isShape:         Boolean,
    expectedType:    Option[ScType],
    ignoreBaseTypes: Boolean = false,
    fromUnderscore:  Boolean = false
  ): ExpressionTypeResult =
    cachedWithRecursionGuard(
      "ScExpression.getTypeAfterImplicitConversion",
      this,
      ExpressionTypeResult(Failure(NlsString.force("Recursive getTypeAfterImplicitConversion"))),
      BlockModificationTracker(this),
      (checkImplicits, isShape, expectedType, adaptTo, ignoreBaseTypes, fromUnderscore)
    ) {
      Tracing.inference(this)

      def isJavaReflectPolymorphic =
        this.scalaLanguageLevelOrDefault >= Scala_2_11 &&
          ScalaPsiUtil.isJavaReflectPolymorphicSignature(this)

      val initialType =
        if (isShape)
          Right(shape(this).getOrElse(Nothing))
        else
          this.getTypeWithoutImplicits(ignoreBaseTypes, fromUnderscore, expectedType)

      val result = {
        (adaptTo, initialType.toOption) match {
          case (Some(expType), Some(tp))
            if !tp.conforms(expType) =>
            //do not try implicit conversions for shape check or already correct type

            val adapted =
              if (this.isSAMEnabled) this.tryAdaptTypeToSAM(tp, expType, fromUnderscore, checkImplicits = checkImplicits)
              else                   None

            adapted match {
              case Some(result) => result.copy(samAdapted = true)
              case None =>
                if (isJavaReflectPolymorphic)        ExpressionTypeResult(Right(expType))
                else if (!checkImplicits || isShape) ExpressionTypeResult(initialType)
                else                                 this.updateTypeWithImplicitConversion(tp, expType)
            }
          case _ => ExpressionTypeResult(initialType)
        }
      }

      Tracing.inference(this, result)

      result
    }
}

object ScExpression {
  final case class ExpressionTypeResult(
    tr:                 TypeResult,
    importsUsed:        Set[ImportUsed]            = Set.empty,
    implicitConversion: Option[ScalaResolveResult] = None,
    samAdapted:         Boolean                    = false
  ) {
    def implicitFunction: Option[PsiNamedElement] = implicitConversion.map(_.element)
  }

  object Type {
    def unapply(exp: ScExpression): Option[ScType] = exp.`type`().toOption
  }

  implicit class Ext(private val expr: ScExpression) extends AnyVal {
    private implicit def elementScope: ElementScope = expr.elementScope
    private implicit def context: Context = Context(expr)

    /**
     * The parameters of the context function literal this expression is converted to, if its expected type
     * is a context function type.
     *
     * The implicit searches in the body of the expression look them up by the expected type computed from its context
     * (see [[org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitProcessor]]), recording the types they are used with,
     * while the expression may be typed against an explicitly given expected type (see [[Typeable.type]]).
     * So the parameters are cached by the expected type, for equal expected types to share them.
     */
    def contextFunctionParameters(pt: Option[ScType] = None): Seq[Seq[LightContextFunctionParameter]] =
      expr match {
        case fun: ScFunctionExpr if fun.isContext => Seq.empty
        case _ =>
          pt.orElse(expectedType(fromUnderscore = false)).toSeq.flatMap {
            case p: ParameterizedType =>
              cachedWithRecursionGuard(
                "contextFunctionParameters",
                expr,
                Seq.empty[Seq[LightContextFunctionParameter]],
                BlockModificationTracker(expr),
                Tuple1(p)
              ) {
                p.contextParameters
              }
            case _ => Seq.empty
          }
      }

    def expectedType(fromUnderscore: Boolean = true): Option[ScType] =
      expectedTypeEx(fromUnderscore).map(_._1)

    def expectedTypeEx(fromUnderscore: Boolean = true): Option[ParameterType] =
      cachedWithRecursionGuard(
        "expectedTypeEx",
        expr,
        Option.empty[ParameterType],
        BlockModificationTracker(expr),
        Tuple1(fromUnderscore)
      ) {
        ExpectedTypes.instance().expectedExprType(expr, fromUnderscore)
      }

    def expectedTypes(fromUnderscore: Boolean = true): Seq[ScType] = expectedTypesEx(fromUnderscore).map(_._1).toSeq

    def expectedTypesEx(fromUnderscore: Boolean = true): Array[ParameterType] =
      cachedWithRecursionGuard(
        "expectedTypesEx",
        expr,
        Array.empty[ParameterType],
        BlockModificationTracker(expr),
        Tuple1(fromUnderscore)
      ) {
        ExpectedTypes.instance().expectedExprTypes(expr, fromUnderscore = fromUnderscore)
      }

    def smartExpectedType(fromUnderscore: Boolean = true): Option[ScType] =
      cachedWithRecursionGuard(
        "smartExpectedType",
        expr,
        Option.empty[ScType],
        BlockModificationTracker(expr),
        Tuple1(fromUnderscore)
      ) {
      ExpectedTypes.instance().smartExpectedType(expr, fromUnderscore)
    }

    def getTypeIgnoreBaseType: TypeResult = expr.getTypeAfterImplicitConversion(ignoreBaseTypes = true).tr

    /**
     * @param fromUnderscore whether to compute the type of the body of the anonymous function in placeholder syntax
     *                       this expression denotes (e.g. `_ + 1`), rather than the type of the function itself.
     * @param expectedType   the expected type of this expression, or the expected type of the body of the anonymous
     *                       function, if `fromUnderscore` is set (see [[ExpectedTypePropagation.underscoreFunctionBody]]).
     *                       Resolve and type inference should always pass it explicitly, see [[Typeable.type]].
     *                       The default value is only correct when `fromUnderscore` is not set.
     */
    def getNonValueType(
      ignoreBaseType: Boolean        = false,
      fromUnderscore: Boolean        = false,
      expectedType:   Option[ScType] = this.expectedType(fromUnderscore = false)
    ): TypeResult =
      cachedWithRecursionGuard(
        "getNonValueType",
        expr,
        Failure(NlsString.force("Recursive getNonValueType")),
        BlockModificationTracker(expr),
        (ignoreBaseType, fromUnderscore, expectedType)
      ) {
        ProgressManager.checkCanceled()

        if (fromUnderscore) expr.innerType(expectedType)
        else {
          val unders = ScUnderScoreSectionUtil.underscores(expr)
          if (unders.isEmpty)
            expr.innerType(expectedType)
          else {
            val params =
              unders.zipWithIndex.map {
                case (u, index) =>
                  val tpe = u.getNonValueType(ignoreBaseType).getOrAny.inferValueType.unpackedType
                  Parameter(tpe, isRepeated = false, index = index)
              }

            val methType =
              ScMethodType(
                expr
                  .getTypeAfterImplicitConversion(
                    expectedOption  = ExpectedTypePropagation.underscoreFunctionBody(expr, expectedType),
                    ignoreBaseTypes = ignoreBaseType,
                    fromUnderscore  = true
                  )
                  .tr
                  .getOrAny,
                params
              )

            Right(methType)
          }
        }
      }

    /**
     * @param expectedType see [[getNonValueType]]
     */
    def getTypeWithoutImplicits(
      ignoreBaseType: Boolean        = false,
      fromUnderscore: Boolean        = false,
      expectedType:   Option[ScType] = this.expectedType(fromUnderscore = false)
    ): TypeResult =
      cachedWithRecursionGuard(
        "getTypeWithoutImplicits",
        expr,
        Failure(NlsString.force("Recursive getTypeWithoutImplicits")),
        BlockModificationTracker(expr),
        (ignoreBaseType, fromUnderscore, expectedType)
      ) {
        ProgressManager.checkCanceled()

        CompilerType(expr) match {
          case Some(s) =>
            val settings = ScalaProjectSettings.getInstance(expr.getProject)
            if (settings.isCompilerHighlightingScala3 && settings.isUseCompilerTypes) {
              Right(ScalaPsiElementFactory.createTypeFromText(s, expr, null).get)
            } else {
              CompilerType(expr) = None
              getTypeWithoutImplicits0(ignoreBaseType, fromUnderscore, expectedType)
            }
          case None =>
            getTypeWithoutImplicits0(ignoreBaseType, fromUnderscore, expectedType)
        }
      }

    private def getTypeWithoutImplicits0(
      ignoreBaseType: Boolean,
      fromUnderscore: Boolean,
      expectedType:   Option[ScType]
    ): TypeResult = {
      expr match {
        case literals.ScNullLiteral(typeWithoutImplicits) => Right(typeWithoutImplicits)
        case _ =>
          val maybeNonValueType = expr.getNonValueType(ignoreBaseType, fromUnderscore, expectedType)
          maybeNonValueType.flatMap { nonValueType =>
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
                    val substed        = subst(tpt)
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


            val withExpected =
              widened
                .dropMethodTypeEmptyParams(expr, expectedType)
                .updateWithExpected(expr, maybeSAMpt.orElse(expectedType), fromUnderscore, expectedType)

            val valueType =
              inferValueType(withExpected)
                .synthesizeContextFunctionType(expectedType, expr)
                .unpackedType
                .synthesizePartialFunctionType(expr, expectedType)
                .untupleFunction(expr, expectedType)

            if (ignoreBaseType) Right(valueType)
            else
              expectedType match {
                case None                                                   => Right(valueType)
                case Some(expected) if expected.removeAbstracts.equiv(Unit) => Right(Unit) //value discarding
                case Some(expected)                                         => Right(numericWideningOrNarrowing(valueType, expected, expr))
              }
          }
      }
    }

    /**
     * Has side effects!
     *
     * @param expectedType the expected type the expression is typed against, see [[getNonValueType]]
     * @param pt           the expected type to update `tpe` according to
     */
    def updateWithImplicitParameters(
      tpe:               ScType,
      checkExpectedType: Boolean,
      fromUnderscore:    Boolean,
      expectedType:      Option[ScType],
      pt:                Option[ScType]
    ): ScType = {
      val updateDeep                 = pt.exists(FunctionType.isFunctionType) || expr.is[ScUnderscoreSection]
      val (newType, implicitClauses) = updatedWithImplicitArguments(tpe, checkExpectedType, updateDeep = updateDeep)

      if (ScUnderScoreSectionUtil.isUnderscoreFunction(expr) == fromUnderscore) {
        expr.setImplicitArguments(implicitClauses, expectedType)
      }

      newType
    }

    def updatedWithImplicitArguments(
      tpe:               ScType,
      checkExpectedType: Boolean,
      updateDeep:        Boolean,
      isLeadingClause:   Boolean = false
    ): (ScType, Seq[ImplicitArgumentsClause]) =  {
      val shouldUpdate = expr.is[MethodInvocation, ScGenericCall] || ScalaPsiUtil.isEtaExpandedExpression(expr)

      if (shouldUpdate) {
        val (updatedType, implicits) =
          InferUtil.updateTypeWithImplicitParameters(
            tpe,
            expr,
            None,
            canThrowSCE     = checkExpectedType,
            fullInfo        = false,
            updateDeep      = updateDeep,
            isLeadingClause = isLeadingClause
          )
        (updatedType, implicits)
      } else (tpe, Seq.empty)
    }

    def implicitConversions(fromUnderscore: Boolean = false): Seq[PsiNamedElement] = {
      ScImplicitlyConvertible.findImplicitConversions(expr, fromUnderscore)
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
  }

  @tailrec
  private def shouldUpdateImplicitParams(expr: ScExpression): Boolean = {
    //true if it wasn't updated in MethodInvocation method
    expr match {
      case prefix: ScPrefixExpr if ScPrefixExpr.isAssignmentLhs(prefix)                                      => false
      case ChildOf(prefix: ScPrefixExpr) if prefix.operation == expr && ScPrefixExpr.isAssignmentLhs(prefix) => false
      case _: ScLiteral                       => false
      case _: ScPrefixExpr                    => true
      case _: ScPostfixExpr                   => true
      case _: ScPolyFunctionExpr              => false
      case ChildOf(ScInfixExpr(_, `expr`, _)) => false //implicit parameters are in infix expression
      case ChildOf(_: ScGenericCall)          => false //implicit parameters are in generic call
      case ChildOf(ScAssignment(`expr`, _))   => false //simple var cannot have implicit parameters, otherwise it's for assignment
      case _: MethodInvocation                => false
      case ScParenthesisedExpr(inner)         => shouldUpdateImplicitParams(inner)
      case fn: ScFunctionExpr if fn.isContext => false
      case gen: ScGenericCall                 =>
        gen.getParent match {
          //`foo[Bar](baz)` or `foo bar[Baz] Qux` case
          //trailing implicits belong to the invocation
          case MethodInvocation(`gen`, _) => false
          //Standalone gen. call `foo[Bar]` -> trailing implicits belong
          //to the call itself
          case _                          => true
        }
      case _ => true
    }
  }

  private implicit class ExprTypeUpdates(private val scType: ScType) extends AnyVal {
    def widenLiteralType(expr: ScExpression, expectedType: Option[ScType]): ScType = {
      implicit val context: Context = Context(expr)

      def isLiteralType(tp: ScType) = tp.removeAliasDefinitions().is[ScLiteralType]

      scType match {
        case lt: ScLiteralType if !expr.literalTypesEnabled && !expectedType.exists(isLiteralType) =>
          lt.wideType
        case _ => scType
      }
    }

    /**
     * if the expected type of expression E is a context function type (T_1, ..., T_n) ?=> U and
     * E is not already a context function literal, E is converted to a context function literal by rewriting it to
     * (x_1: T1, ..., x_n: Tn) ?=> E
     */
    final def synthesizeContextFunctionType(pt: Option[ScType], expr: ScExpression)(implicit scope: ElementScope, context: Context): ScType =
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

              val actualParamTypes =
                expr
                  .contextFunctionParameters(pt)
                  .map(_.map(_.contextFunctionParameterType.getOrAny))

              val res = actualParamTypes.foldRight(scType) {
                case (params, acc) => ContextFunctionType(acc, params)
              }
              res
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
    )(implicit context: Context): ScType =
      if (expr.isInScala3Module && SAMUtil.isFunctionalExpression(expr))
        scType match {
          case FunctionType(resTpe, paramTypes) =>
            pt.map(_.removeAliasDefinitions()) match {
              case Some(FunctionType(ptRes, Seq(TupleType(ptParams))))
                if resTpe.conforms(ptRes) && parameterTypesMatch(paramTypes, ptParams) =>
                // The tuple of the parameter types rather than the expected tuple type (which conforms to it),
                // since the latter may refer to existential arguments, which are approximated in the parameter types,
                // e.g. `K` of `Iterator[(K, V)] forSome { type K }`
                implicit val scope: ElementScope = expr.elementScope
                FunctionType((resTpe, Seq(TupleType(paramTypes, expr))))
              case _ => scType
            }
          case _ => scType
        }
      else scType

    private def parameterTypesMatch(params: Seq[ScType], ptParams: Seq[ScType])(implicit context: Context): Boolean =
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
      implicit val elementScope: ElementScope = expr.elementScope
      implicit val context: Context = Context(expr)

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
            else TupleType(paramTpes, context = expr)

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
      implicit val context: Context = Context(expr)

      @scala.annotation.tailrec
      def checkForSAM(tp: ScType): Option[ScType] =
        tp match {
          case FunctionType(_, _)           => SAMUtil.SAMToFunctionType(expected, expr)
          case _: ScMethodType              => SAMUtil.SAMToFunctionType(expected, expr)
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

    private def shouldApplyContextParameters(pt: ScType)(implicit context: Context): Boolean =
      (scType, pt) match {
        case (ContextFunctionType(_, _), ContextFunctionType(_, _)) => false
        case _                                                      => true
      }

    /**
     * Whether the implicit arguments of `expr` have already been applied to the expression the type
     * arguments are applied to, in [[org.jetbrains.plugins.scala.lang.psi.impl.expr.ScGenericCallImpl]],
     * which is where a leading using clause is handled (`def foo(using A)[B]: Int` called as `foo[Int]`).
     * They are already recorded then, and updating them again would only forget them.
     */
    private def implicitParamsAppliedToReferencedExpr(expr: ScExpression): Boolean = expr match {
      case gen: ScGenericCall if !scType.is[ScTypePolymorphicType, ScMethodType] =>
        gen.referencedExpr.getNonValueType(expectedType = None).exists {
          case mt: ScMethodType => mt.isImplicit
          case _                => false
        }
      case _ => false
    }

    /**
     * @param pt           the expected type to update the type according to
     * @param expectedType the expected type the expression is typed against, see [[updateWithImplicitParameters]]
     */
    def updateWithExpected(
      expr:           ScExpression,
      pt:             Option[ScType],
      fromUnderscore: Boolean,
      expectedType:   Option[ScType]
    ): ScType = {
      implicit val context: Context = Context(expr)

      if (shouldUpdateImplicitParams(expr) && !implicitParamsAppliedToReferencedExpr(expr) &&
        pt.forall(shouldApplyContextParameters)) {
        try {
          val updatedWithExpected =
            InferUtil.updateAccordingToExpectedType(
              scType,
              filterTypeParams = false,
              expectedType     = pt,
              expr             = expr,
              canThrowSCE      = true
            )

          expr.updateWithImplicitParameters(
            updatedWithExpected,
            checkExpectedType = true,
            fromUnderscore    = fromUnderscore,
            expectedType      = expectedType,
            pt                = pt
          )
        } catch {
          case _: SafeCheckException =>
            expr.updateWithImplicitParameters(
              typeForFailedImplicitSearch(expr, pt),
              checkExpectedType = false,
              fromUnderscore    = fromUnderscore,
              expectedType      = expectedType,
              pt                = pt
            )
        }
      } else scType
    }

    /**
     * No implicit argument was found for the type parameters inferred from the expected type.
     *
     * Retry without the expected type: an implicit conversion applied to the result may still make
     * the expression typecheck. But if that does not find the implicit arguments either, keep the
     * type parameters inferred from the expected type, so that the failure is reported for the type
     * that is actually required (`no implicit Int found` for `val i: Int = summon`, rather than
     * `no implicit search was attempted, T is not specific enough`).
     */
    private def typeForFailedImplicitSearch(
      expr:         ScExpression,
      expectedType: Option[ScType]
    )(implicit context: Context): ScType = {
      val updateDeep   = expectedType.exists(FunctionType.isFunctionType) || expr.is[ScUnderscoreSection]
      val (_, clauses) = expr.updatedWithImplicitArguments(scType, checkExpectedType = false, updateDeep = updateDeep)

      if (!clauses.exists(_.args.exists(_.isImplicitParameterProblem))) scType
      else
        InferUtil.updateAccordingToExpectedType(
          scType,
          filterTypeParams = false,
          expectedType     = expectedType,
          expr             = expr,
          canThrowSCE      = false
        )
    }

    def dropMethodTypeEmptyParams(expr: ScExpression, expectedType: Option[ScType]): ScType = {
      implicit val context: Context = Context(expr)

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

  /**
   * Whether a function literal with several parameters is untupled to a function of a single tuple parameter,
   * when it is typed against `pt`, see `untupleFunction`
   */
  def isUntupledTo(literal: ScExpression, pt: ScType): Boolean = {
    implicit val context: Context = Context(literal)

    shape(literal).exists { shapeType =>
      shapeType.untupleFunction(isAnonymousExpression(literal)._2, Option(pt)) ne shapeType
    }
  }

  private def shape(expression: ScExpression, ignoreAssign: Boolean = false): Option[ScType] = {
    import expression.{elementScope, projectContext}
    implicit val context: Context = Context(expression)

    def shapeIgnoringAssign(maybeExpression: Option[ScExpression]) = maybeExpression.flatMap {
      shape(_, ignoreAssign = true)
    }

    expression match {
      case assign: ScAssignment if !ignoreAssign && assign.referenceName.isDefined =>
        shapeIgnoringAssign(assign.rightExpression)
      // the shape of a pattern-matching anonymous function `{ case ... }` is `PartialFunction[Any, Nothing]` since Scala 2.13,
      // see `scala.tools.nsc.typechecker.Typers.shapeType` and `dotty.tools.dotc.typer.Applications.typeShape`
      case block: ScBlockExpr if block.isPartialFunction && expression.scalaLanguageLevelOrDefault >= Scala_2_13 =>
        Some(PartialFunctionType((Nothing, Any)))
      case _ =>
        val arityAndResultType = Option(isAnonymousExpression(expression)).filter {
          case (-1, _) => false
          case _ => true
        }.map {
          case (i, expr: ScFunctionExpr) => (i, shapeIgnoringAssign(expr.result), expr.isContext)
          case (i, _) => (i, None, false)
        }

        arityAndResultType.map {
          case (i, tp, isContext) => (Seq.fill(i)(Any), tp, isContext)
        }.map {
          // the shape of a context function literal `(x1, ..., xn) ?=> e` is `(Any, ..., Any) ?=> shape(e)`
          case (argumentsTypes, maybeResultType, true) =>
            ContextFunctionType(maybeResultType.getOrElse(Nothing), argumentsTypes)
          case (argumentsTypes, maybeResultType, false) =>
            FunctionType(maybeResultType.getOrElse(Nothing), argumentsTypes)
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
