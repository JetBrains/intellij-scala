package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiNamedElementExt, StringExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{MethodValue, isAnonymousExpression}
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.{SafeCheckException, extractImplicitParameterType}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScIntLiteral, ScLiteral, TreeMember}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.implicits.{ImplicitCollector, ImplicitResolveResult, ScImplicitlyConvertible}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, StdKinds}
import org.jetbrains.plugins.scala.macroAnnotations.{CachedWithRecursionGuard, ModCount}
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_11
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor.conformsToDynamic

import scala.annotation.tailrec
import scala.collection.mutable
import org.jetbrains.plugins.scala.lang.psi.api.expr.ExpectedTypes.ParameterType

/**
  * @author ilyas, Alexander Podkhalyuzin
  */

trait ScExpression extends ScBlockStatement with PsiAnnotationMemberValue with ImplicitParametersOwner
  with ScModificationTrackerOwner with Typeable with TreeMember[ScExpression] {

  import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression._

  override def `type`(): TypeResult =
    this.getTypeAfterImplicitConversion().tr

  override def isSameTree(p: PsiElement): Boolean = p.isInstanceOf[ScExpression]

  @volatile
  private var implicitParameters: Option[Seq[ScalaResolveResult]] = None

  @volatile
  private var implicitParametersFromUnder: Option[Seq[ScalaResolveResult]] = None

  final protected def setImplicitParameters(results: Option[Seq[ScalaResolveResult]],
                                            fromUnderscore: Boolean = ScUnderScoreSectionUtil.isUnderscoreFunction(this)): Unit = {
    if (fromUnderscore)
      implicitParametersFromUnder = results
    else
      implicitParameters = results
  }

  /**
    * Warning! There is a hack in scala compiler for ClassManifest and ClassTag.
    * In case of implicit parameter with type ClassManifest[T]
    * this method will return ClassManifest with substitutor of type T.
    *
    * @return implicit parameters used for this expression
    */
  def findImplicitParameters: Option[Seq[ScalaResolveResult]] = {
    ProgressManager.checkCanceled()

    if (ScUnderScoreSectionUtil.isUnderscoreFunction(this)) {
      this.getTypeWithoutImplicits(fromUnderscore = true) //to update implicitParametersFromUnder
      implicitParametersFromUnder
    } else {
      `type`() //to update implicitParameters field
      implicitParameters
    }
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
      createExpressionFromText(expr.getText.parenthesize())
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
      case generator: ScGenerator => inner(generator)
      case _ =>
        this.getTypeAfterImplicitConversion(expectedOption = expectedOption, fromUnderscore = fromUnderscore).implicitConversion
    }

    inner(this)
  }

  def getAllImplicitConversions(fromUnderscore: Boolean = false): Seq[PsiNamedElement] = {
    new ScImplicitlyConvertible(this, fromUnderscore)
      .implicitMap(arguments = this.expectedTypes(fromUnderscore).toSeq)
      .map(_.element)
      .sortWith {
        case (first, second) =>
          val firstName = first.name
          val secondName = second.name

          def isAnyTo(string: String): Boolean =
            string.matches("^[a|A]ny(2|To|to).+$")

          val isSecondAnyTo = isAnyTo(secondName)

          if (isAnyTo(firstName) ^ isSecondAnyTo) isSecondAnyTo
          else firstName.compareTo(secondName) < 0
      }
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

    override def visitTryExpression(statement: ScTryStmt): Unit = {
      acceptVisitor(statement.tryBlock)

      statement.catchBlock.collect {
        case ScCatchBlock(clauses) => clauses
      }.toSeq
        .flatMap(_.caseClauses)
        .flatMap(_.expr)
        .foreach(acceptVisitor)
    }

    override def visitExprInParent(expression: ScParenthesisedExpr): Unit = {
      expression.expr match {
        case Some(innerExpression) => acceptVisitor(innerExpression)
        case _ => super.visitExprInParent(expression)
      }
    }

    override def visitMatchStatement(statement: ScMatchStmt): Unit = {
      statement.getBranches.foreach(acceptVisitor)
    }

    override def visitIfStatement(statement: ScIfStmt): Unit = {
      statement.elseBranch match {
        case Some(elseBranch) =>
          acceptVisitor(elseBranch)
          statement.thenBranch.foreach(acceptVisitor)
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

  implicit class Ext(val expr: ScExpression) extends AnyVal {
    private implicit def elementScope: ElementScope = expr.elementScope

    private def project = elementScope.projectContext

    def expectedType(fromUnderscore: Boolean = true): Option[ScType] =
      expectedTypeEx(fromUnderscore).map(_._1)

    def expectedTypeEx(fromUnderscore: Boolean = true): Option[ParameterType] =
      ExpectedTypes.instance().expectedExprType(expr, fromUnderscore)

    def expectedTypes(fromUnderscore: Boolean = true): Array[ScType] = expectedTypesEx(fromUnderscore).map(_._1)

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

      val expected = expectedOption.orElse {
        expectedType(fromUnderscore = fromUnderscore)
      }

      if (isShape) ExpressionTypeResult(Right(shape(expr).getOrElse(Nothing)))
      else {
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
                  val `type` = extractImplicitParameterType(res).flatMap {
                    case FunctionType(rt, Seq(_)) => Some(rt)
                    case paramType =>
                      expr.elementScope.cachedFunction1Type.flatMap { functionType =>
                        val (_, substitutor) = paramType.conforms(functionType, ScUndefinedSubstitutor())
                        substitutor.getSubstitutor.map {
                          _.subst(functionType.typeArguments(1))
                        }.filter {
                          !_.isInstanceOf[UndefinedType]
                        }
                      }
                  }

                  `type`.map((_, res))
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
      val fromNullLiteral = expr match {
        case lit: ScLiteral =>
          val typeForNull = lit.getTypeForNullWithoutImplicits
          if (typeForNull.nonEmpty) Option(Right(typeForNull.get))
          else None
        case _ => None
      }

      if (fromNullLiteral.nonEmpty) fromNullLiteral.get
      else {
        val inner = expr.getNonValueType(ignoreBaseTypes, fromUnderscore)
        inner match {
          case Right(rtp) =>
            var res = rtp

            val expType = expectedType(fromUnderscore)

            def updateExpected(oldRes: ScType): ScType = {
              try {
                val updatedWithExpected =
                  InferUtil.updateAccordingToExpectedType(rtp, fromImplicitParameters = true,
                    filterTypeParams = false, expectedType = expType, expr = expr, canThrowSCE = true)
                updatedWithExpected match {
                  case newRes =>
                    updateWithImplicitParameters(newRes, checkExpectedType = true, fromUnderscore)
                  case _ =>
                    updateWithImplicitParameters(oldRes, checkExpectedType = true, fromUnderscore)
                }
              } catch {
                case _: SafeCheckException =>
                  updateWithImplicitParameters(oldRes, checkExpectedType = false, fromUnderscore)
              }
            }

            if (!isMethodInvocation(expr)) {
              //it is not updated according to expected type, let's do it
              res = updateExpected(rtp)
            }

            def removeMethodType(retType: ScType, updateType: ScType => ScType = t => t) {
              expType match {
                case Some(expected) =>
                  expected.removeAbstracts match {
                    case FunctionType(_, _) =>
                    case expect if ScalaPsiUtil.isSAMEnabled(expr) =>
                      val languageLevel = expr.scalaLanguageLevelOrDefault
                      if (languageLevel != Scala_2_11 || ScalaPsiUtil.toSAMType(expect, expr).isEmpty) {
                        res = updateType(retType)
                      }
                    case _ => res = updateType(retType)
                  }
                case _ => res = updateType(retType)
              }
            }

            res match {
              case ScTypePolymorphicType(ScMethodType(retType, params, _), tp) if params.isEmpty &&
                !ScUnderScoreSectionUtil.isUnderscore(expr) =>
                removeMethodType(retType, t => ScTypePolymorphicType(t, tp))
              case ScMethodType(retType, params, _) if params.isEmpty &&
                !ScUnderScoreSectionUtil.isUnderscore(expr) =>
                removeMethodType(retType)
              case _ =>
            }

            val valType = res.inferValueType.unpackedType

            if (ignoreBaseTypes) Right(valType)
            else {
              expType match {
                case None =>
                  Right(valType)
                case Some(expected) if expected.removeAbstracts equiv Unit =>
                  //value discarding
                  Right(Unit)
                case Some(expected) =>
                  val narrowing = isNarrowing(expected)
                  if (narrowing.isDefined) narrowing.get
                  else {
                    val widening = isWidening(valType, expected)
                    if (widening.isDefined) widening.get
                    else Right(valType)
                  }
              }
            }
          case _ => inner
        }
      }
    }

    //has side effect!
    private def updateWithImplicitParameters(tpe: ScType, checkExpectedType: Boolean, fromUnderscore: Boolean): ScType = {
      val (newType, params) = updatedWithImplicitParameters(tpe, checkExpectedType)

      expr.setImplicitParameters(params, fromUnderscore)

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

    private def getStdType(t: ScType): Option[StdType] = {
      val stdTypes = project.stdTypes
      import stdTypes._

      t match {
        case AnyVal => Some(AnyVal)
        case valType: ValType => Some(valType)
        case designatorType: ScDesignatorType => designatorType.getValType
        case _ => None
      }
    }

    @tailrec
    private def isMethodInvocation(expr: ScExpression): Boolean = {
      expr match {
        case _: ScPrefixExpr => false
        case _: ScPostfixExpr => false
        case _: MethodInvocation => true
        case p: ScParenthesisedExpr =>
          p.expr match {
            case Some(exp) => isMethodInvocation(exp)
            case _ => false
          }
        case _ => false
      }
    }

    private def tryConvertToSAM(fromUnderscore: Boolean, expected: ScType, tp: ScType) = {
      def checkForSAM(etaExpansionHappened: Boolean = false): Option[ExpressionTypeResult] = {
        def expectedResult = Some(ExpressionTypeResult(Right(expected)))

        tp match {
          case FunctionType(_, params) if ScalaPsiUtil.isSAMEnabled(expr) =>
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

    private def removeMethodType(tp: ScType, expected: ScType): ScType = {
      def inner(retType: ScType, updateType: ScType => ScType): ScType = {
        expected.removeAbstracts match {
          case FunctionType(_, _) => tp
          case expect if ScalaPsiUtil.isSAMEnabled(expr) =>
            val languageLevel = expr.scalaLanguageLevelOrDefault
            if (languageLevel != Scala_2_11 || ScalaPsiUtil.toSAMType(expect, expr).isEmpty) {
              updateType(retType)
            }
            else tp
          case _ => updateType(retType)
        }
      }

      tp match {
        case ScTypePolymorphicType(ScMethodType(retType, params, _), typeParams) if params.isEmpty &&
          !ScUnderScoreSectionUtil.isUnderscore(expr) =>
          inner(retType, t => ScTypePolymorphicType(t, typeParams))
        case ScMethodType(retType, params, _) if params.isEmpty &&
          !ScUnderScoreSectionUtil.isUnderscore(expr) =>
          inner(retType, t => t)
        case _ => tp
      }
    }
  }

  private def shape(expression: ScExpression, ignoreAssign: Boolean = false): Option[ScType] = {
    import expression.projectContext

    def shapeIgnoringAssign(maybeExpression: Option[ScExpression]) = maybeExpression.flatMap {
      shape(_, ignoreAssign = true)
    }

    expression match {
      case assign: ScAssignStmt if !ignoreAssign && assign.assignName.isDefined =>
        shapeIgnoringAssign(assign.getRExpression)
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
