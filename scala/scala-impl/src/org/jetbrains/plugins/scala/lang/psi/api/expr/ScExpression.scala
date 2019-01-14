package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import java.util.concurrent.atomic.AtomicLong

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{MethodValue, isAnonymousExpression}
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.{SafeCheckException, extractImplicitParameterType}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScIntLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ExpectedTypes.ParameterType
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.impl
import org.jetbrains.plugins.scala.lang.psi.implicits.{ImplicitCollector, ScImplicitlyConvertible}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, CachedWithRecursionGuard, ModCount}
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_11

import scala.annotation.tailrec
import scala.collection.mutable

/**
  * @author ilyas, Alexander Podkhalyuzin
  */
trait ScExpression extends ScBlockStatement
  with PsiAnnotationMemberValue
  with PsiModifiableCodeBlock
  with ImplicitArgumentsOwner
  with Typeable {

  import ScExpression._

  private[this] val blockModificationCount = new AtomicLong()

  final def modificationCount: Long = blockModificationCount.get

  final def incModificationCount(): Unit = blockModificationCount.incrementAndGet()

  @Cached(ModCount.getBlockModificationCount, this)
  final def mirrorPosition(dummyIdentifier: String, offset: Int): Option[PsiElement] = {
    val index = offset - getTextRange.getStartOffset

    val text = new StringBuilder(getText).insert(index, dummyIdentifier).toString

    for {
      methodCall <- impl.ScalaPsiElementFactory.createMirrorElement(text, getContext, this)
      element <- Option(methodCall.findElementAt(index))
    } yield element
  }

  //element is always the child of this element because this function is called when going up the tree starting with elem
  //if this is a valid modification tracker owner, no need to change modification count
  override final def shouldChangeModificationCount(element: PsiElement): Boolean = getContext match {
    case f: ScFunction => f.returnTypeElement.isEmpty && f.hasAssign
    case v: ScValueOrVariable => v.typeElement.isEmpty
    case _: ScWhile |
         _: ScFinallyBlock |
         _: ScTemplateBody |
         _: ScDo => false
    //expression is not last in a block and not assigned to anything, cannot affect type inference outside
    case _: ScBlock =>
      this.nextSiblings.forall {
        case _: ScExpression => false
        case _ => true
      }
    case _ => true
  }

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

  def setAdditionalExpression(additionalExpression: Option[(ScExpression, ScType)]) {
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
}

object ScExpression {

  def calculateReturns(expression: ScExpression): Set[ScExpression] = {
    val visitor = new ReturnsVisitor
    expression.accept(visitor)
    visitor.result
  }

  private[api] class ReturnsVisitor extends ScalaElementVisitor {

    private val result_ = mutable.LinkedHashSet.empty[ScExpression]

    def result: Set[ScExpression] = result_.toSet

    override def visitTryExpression(statement: ScTry): Unit = {
      acceptVisitor(statement.tryBlock)

      statement.catchBlock.collect {
        case ScCatchBlock(clauses) => clauses
      }.toSeq
        .flatMap(_.caseClauses)
        .flatMap(_.expr)
        .foreach(acceptVisitor)
    }

    override def visitExprInParent(expression: ScParenthesisedExpr): Unit = {
      expression.innerElement match {
        case Some(innerExpression) => acceptVisitor(innerExpression)
        case _ => super.visitExprInParent(expression)
      }
    }

    override def visitMatchStatement(statement: ScMatch): Unit = {
      statement.expressions.foreach(acceptVisitor)
    }

    override def visitIfStatement(statement: ScIf): Unit = {
      statement.elseExpression match {
        case Some(elseBranch) =>
          acceptVisitor(elseBranch)
          statement.thenExpression.foreach(acceptVisitor)
        case _ => super.visitIfStatement(statement)
      }
    }

    override def visitReferenceExpression(reference: ScReferenceExpression): Unit = {
      visitExpression(reference)
    }

    override def visitExpression(expression: ScExpression): Unit = {
      val maybeLastExpression = expression match {
        case block: ScBlock => block.lastExpr
        case _ => None
      }

      maybeLastExpression match {
        case Some(lastExpression) =>
          acceptVisitor(lastExpression)
        case _ =>
          super.visitExpression(expression)
          result_ += expression
      }
    }

    protected def acceptVisitor(expression: ScExpression): Unit = {
      expression.accept(this)
    }
  }

  case class ExpressionTypeResult(tr: TypeResult,
                                  importsUsed: scala.collection.Set[ImportUsed] = Set.empty,
                                  implicitConversion: Option[ScalaResolveResult] = None) {
    def implicitFunction: Option[PsiNamedElement] = implicitConversion.map(_.element)
  }

  object Type {
    def unapply(exp: ScExpression): Option[ScType] = exp.`type`().toOption
  }

  implicit class Ext(private val expr: ScExpression) extends AnyVal {
    private implicit def elementScope: ElementScope = expr.elementScope

    private def project = elementScope.projectContext

    def shouldntChangeModificationCount: Boolean =
      !expr.shouldChangeModificationCount(null)

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

    def getTypeIgnoreBaseType: TypeResult = getTypeAfterImplicitConversion(ignoreBaseTypes = true).tr

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
            ScMethodType(expr.getTypeAfterImplicitConversion(ignoreBaseTypes = ignoreBaseType,
              fromUnderscore = true).tr.getOrAny,
              params, isImplicit = false)
          Right(methType)
        }
      }
    }

    /**
      * This method returns real type, after using implicit conversions.
      * Second parameter to return is used imports for this conversion.
      *
      * @param expectedOption  to which type we trying to convert
      * @param ignoreBaseTypes parameter to avoid value discarding, literal narrowing, widening
      *                        this parameter is useful for refactorings (introduce variable)
      */
    @CachedWithRecursionGuard(expr, ExpressionTypeResult(Failure("Recursive getTypeAfterImplicitConversion")), ModCount.getBlockModificationCount)
    def getTypeAfterImplicitConversion(checkImplicits: Boolean = true,
                                       isShape: Boolean = false,
                                       expectedOption: Option[ScType] = None,
                                       ignoreBaseTypes: Boolean = false,
                                       fromUnderscore: Boolean = false): ExpressionTypeResult = {

      def isJavaReflectPolymorphic = expr.scalaLanguageLevelOrDefault >= Scala_2_11 && ScalaPsiUtil.isJavaReflectPolymorphicSignature(expr)

      if (isShape) ExpressionTypeResult(Right(shape(expr).getOrElse(Nothing)))
      else {
        val expected = expectedOption.orElse {
          expectedType(fromUnderscore = fromUnderscore)
        }
        val tr = getTypeWithoutImplicits(ignoreBaseTypes, fromUnderscore)
        (expected, tr.toOption) match {
          case (Some(expType), Some(tp))
            if checkImplicits && !tp.conforms(expType) => //do not try implicit conversions for shape check or already correct type

            val samType = tryConvertToSAM(fromUnderscore, expType, tp)

            if (samType.nonEmpty) samType.get
            else if (isJavaReflectPolymorphic) ExpressionTypeResult(Right(expType))
            else {
              val functionType = FunctionType(expType, Seq(tp))
              val implicitCollector = new ImplicitCollector(expr, functionType, functionType, None, isImplicitConversion = true)
              val fromImplicit = implicitCollector.collect() match {
                case Seq(res) =>
                  extractImplicitParameterType(res).flatMap {
                    case FunctionType(rt, Seq(_)) => Some(rt)
                    case paramType =>
                      expr.elementScope.cachedFunction1Type.flatMap { functionType =>
                        paramType.conforms(functionType, ConstraintSystem.empty) match {
                          case ConstraintSystem(substitutor) => Some(substitutor(functionType.typeArguments(1)))
                          case _ => None
                        }
                      }.filterNot {
                        _.isInstanceOf[UndefinedType]
                      }
                  }.map(_ -> res)
                case _ => None
              }
              fromImplicit match {
                case Some((mr, result)) =>
                  ExpressionTypeResult(Right(mr), result.importsUsed, Some(result))
                case _ =>
                  ExpressionTypeResult(tr)
              }
            }
          case _ => ExpressionTypeResult(tr)
        }
      }
    }

    @CachedWithRecursionGuard(expr, Failure("Recursive getTypeWithoutImplicits"),
      ModCount.getBlockModificationCount)
    def getTypeWithoutImplicits(ignoreBaseTypes: Boolean = false, fromUnderscore: Boolean = false): TypeResult = {
      ProgressManager.checkCanceled()

      val fromNullLiteral = expr.asOptionOf[ScLiteral].flatMap(_.getTypeForNullWithoutImplicits)

      if (fromNullLiteral.nonEmpty) Right(fromNullLiteral.get)
      else {
        expr.getNonValueType(ignoreBaseTypes, fromUnderscore) match {
          case Right(nonValueType) =>

            val expected = expectedType(fromUnderscore)

            val valueType =
              nonValueType
                .widenLiteralType(expr, expected)
                .updateWithExpected(expr, expected, fromUnderscore)
                .dropMethodTypeEmptyParams(expr, expected)
                .inferValueType
                .unpackedType

            if (ignoreBaseTypes) Right(valueType)
            else {
              expected match {
                case None                                         => Right(valueType)
                case Some(exp) if exp.removeAbstracts.equiv(Unit) => Right(Unit) //value discarding
                case Some(exp)                                    => numericWideningOrNarrowing(valueType, exp)
              }
            }
          case fail => fail
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
        case ScIntLiteral(value) => value
        case ScPrefixExpr(op, ScIntLiteral(value)) if Set("+", "-").contains(op.refName) =>
          val mult = if (op.refName == "-") -1 else 1
          mult * value
        case _ => return None
      }

      val stdTypes = StdTypes.instance
      import stdTypes._

      expected.removeAbstracts match {
        case Char if isChar(intLiteralValue) => success(Char)
        case Byte if isByte(intLiteralValue) => success(Byte)
        case Short if isShort(intLiteralValue) => success(Short)
        case _ => None
      }
    }

    def implicitConversions(fromUnderscore: Boolean = false): Seq[PsiNamedElement] = {
      ScImplicitlyConvertible.implicits(fromUnderscore)(expr)
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
        case _ => return None
      }
      val stdTypes = project.stdTypes
      import stdTypes._

      (l, r) match {
        case (Byte, Short | Int | Long | Float | Double) => Some(Right(expected))
        case (Short, Int | Long | Float | Double) => Some(Right(expected))
        case (Char, Byte | Short | Int | Long | Float | Double) => Some(Right(expected))
        case (Int, Long | Float | Double) => Some(Right(expected))
        case (Long, Float | Double) => Some(Right(expected))
        case (Float, Double) => Some(Right(expected))
        case _ => None
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

    private def tryConvertToSAM(fromUnderscore: Boolean, expected: ScType, tp: ScType) = {
      def checkForSAM(etaExpansionHappened: Boolean = false): Option[ExpressionTypeResult] = {
        def expectedResult = Some(ExpressionTypeResult(Right(expected)))

        tp match {
          case FunctionType(_, params) if expr.isSAMEnabled =>
            ScalaPsiUtil.toSAMType(expected, expr) match {
              case Some(methodType) if tp.conforms(methodType) => expectedResult
              case Some(methodType@FunctionType(retTp, _)) if etaExpansionHappened && retTp.equiv(Unit) =>
                val newTp = FunctionType(Unit, params)
                if (newTp.conforms(methodType)) expectedResult
                else None
              case _ => None
            }
          case _ => None
        }
      }

      expr match {
        case ScFunctionExpr(_, _) if fromUnderscore => checkForSAM()
        case _ if !fromUnderscore && ScalaPsiUtil.isAnonExpression(expr) => checkForSAM()
        case MethodValue(method) if expr.scalaLanguageLevelOrDefault == Scala_2_11 || method.getParameterList.getParametersCount > 0 =>
          checkForSAM(etaExpansionHappened = true)
        case _ => None
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

  private implicit class ExprTypeUpdates(val scType: ScType) extends AnyVal {

    def widenLiteralType(expr: ScExpression, expectedType: Option[ScType]): ScType = {
      def isLiteralType(tp: ScType) = tp.removeAliasDefinitions().isInstanceOf[ScLiteralType]

      scType match {
        case lt: ScLiteralType if !expr.literalTypesEnabled && !expectedType.exists(isLiteralType) =>
          lt.wideType
        case _ => scType
      }
    }

    def updateWithExpected(expr: ScExpression, expectedType: Option[ScType], fromUnderscore: Boolean): ScType = {

      if (shouldUpdateImplicitParams(expr)) {
        try {
          val updatedWithExpected =
            InferUtil.updateAccordingToExpectedType(scType,
              filterTypeParams = false, expectedType = expectedType, expr = expr, canThrowSCE = true)

          expr.updateWithImplicitParameters(updatedWithExpected, checkExpectedType = true, fromUnderscore)
        } catch {
          case _: SafeCheckException =>
            expr.updateWithImplicitParameters(scType, checkExpectedType = false, fromUnderscore)
        }
      }
      else scType
    }

    def dropMethodTypeEmptyParams(expr: ScExpression, expectedType: Option[ScType]): ScType = {
      val (retType, typeParams) = scType match {
        case ScTypePolymorphicType(ScMethodType(rt, params, _), tps) if params.isEmpty => (rt, Some(tps))
        case ScMethodType(rt, params, _) if params.isEmpty                             => (rt, None)
        case _                                                                         => return scType
      }

      val shouldDrop = !ScUnderScoreSectionUtil.isUnderscore(expr) && expectedType.map(_.removeAbstracts).forall {
        case FunctionType(_, _) => false
        case expect if expr.isSAMEnabled =>
          val languageLevel = expr.scalaLanguageLevelOrDefault
          languageLevel != Scala_2_11 || ScalaPsiUtil.toSAMType(expect, expr).isEmpty
        case _ => true
      }

      if (!shouldDrop) scType
      else typeParams.map(ScTypePolymorphicType(retType, _)).getOrElse(retType)
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
