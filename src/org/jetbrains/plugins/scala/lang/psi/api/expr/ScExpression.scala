package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.{ElementText, PsiElementExt, PsiNamedElementExt, StringExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{MethodValue, isAnonymousExpression}
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.{SafeCheckException, extractImplicitParameterType}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScIntLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.implicits.{ImplicitCollector, ImplicitResolveResult, ScImplicitlyConvertible}
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{api, _}
import org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, StdKinds}
import org.jetbrains.plugins.scala.macroAnnotations.{CachedWithRecursionGuard, ModCount}
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_11

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.collection.{Seq, Set}

/**
  * @author ilyas, Alexander Podkhalyuzin
  */

trait ScExpression extends ScBlockStatement with PsiAnnotationMemberValue with ImplicitParametersOwner
  with ScModificationTrackerOwner with Typeable {

  import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression._

  override def getType(ctx: TypingContext): TypeResult[ScType] =
    this.getTypeAfterImplicitConversion().tr

  @volatile
  private var implicitParameters: Option[Seq[ScalaResolveResult]] = None

  @volatile
  private var implicitParametersFromUnder: Option[Seq[ScalaResolveResult]] = None

  final protected def setImplicitParameters(results: Option[Seq[ScalaResolveResult]], fromUnderscore: Boolean): Unit = {
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
      getType(TypingContext.empty) //to update implicitParameters field
      implicitParameters
    }
  }

  protected def innerType: TypeResult[ScType] =
    Failure(ScalaBundle.message("no.type.inferred", getText), Some(this))

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
      createExpressionFromText(expr.getText.parenthesize(needParenthesis = true))
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
    getType(TypingContext.empty)
    additionalExpression
  }

  def implicitElement(fromUnderscore: Boolean = false,
                      expectedOption: => Option[ScType] = this.smartExpectedType()): Option[PsiNamedElement] = {
    def referenceImplicitFunction(reference: ScReferenceExpression) = reference.multiResolve(false) match {
      case Array(result: ScalaResolveResult) => result.implicitFunction
      case _ => None
    }

    def implicitFunction(element: ScalaPsiElement): Option[PsiNamedElement] = element.getParent match {
      case reference: ScReferenceExpression =>
        referenceImplicitFunction(reference)
      case infix: ScInfixExpr if this == infix.getBaseExpr =>
        referenceImplicitFunction(infix.operation)
      case call: ScMethodCall => call.getImplicitFunction
      case generator: ScGenerator => implicitFunction(generator)
      case _ =>
        this.getTypeAfterImplicitConversion(expectedOption = expectedOption, fromUnderscore = fromUnderscore).implicitFunction
    }

    implicitFunction(this)
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

  final def calculateReturns(withBooleanInfix: Boolean = false): Seq[PsiElement] = {
    val res = new ArrayBuffer[PsiElement]

    def calculateReturns0(el: PsiElement) {
      el match {
        case tr: ScTryStmt =>
          calculateReturns0(tr.tryBlock)
          tr.catchBlock match {
            case Some(ScCatchBlock(caseCl)) =>
              caseCl.caseClauses.flatMap(_.expr).foreach(calculateReturns0)
            case _ =>
          }
        case block: ScBlock =>
          block.lastExpr match {
            case Some(expr) => calculateReturns0(expr)
            case _ => res += block
          }
        case pe: ScParenthesisedExpr =>
          pe.expr.foreach(calculateReturns0)
        case m: ScMatchStmt =>
          m.getBranches.foreach(calculateReturns0)
        case i: ScIfStmt =>
          i.elseBranch match {
            case Some(e) =>
              calculateReturns0(e)
              i.thenBranch match {
                case Some(thenBranch) => calculateReturns0(thenBranch)
                case _ =>
              }
            case _ => res += i
          }
        case ScInfixExpr(left, ElementText(op), right)
          if withBooleanInfix && (op == "&&" || op == "||") &&
            left.getType(TypingContext.empty).exists(_ == api.Boolean) &&
            right.getType(TypingContext.empty).exists(_ == api.Boolean) => calculateReturns0(right)
        //TODO "!contains" is a quick fix, function needs unit testing to validate its behavior
        case _ => if (!res.contains(el)) res += el
      }
    }

    calculateReturns0(this)
    res
  }
}

object ScExpression {

  case class ExpressionTypeResult(tr: TypeResult[ScType],
                                  importsUsed: scala.collection.Set[ImportUsed] = Set.empty,
                                  implicitFunction: Option[PsiNamedElement] = None)

  object Type {
    def unapply(exp: ScExpression): Option[ScType] = exp.getType(TypingContext.empty).toOption
  }

  implicit class Ext(val expr: ScExpression) extends AnyVal {
    private implicit def elementScope = expr.elementScope
    private def project = elementScope.projectContext

    def expectedType(fromUnderscore: Boolean = true): Option[ScType] =
      expectedTypeEx(fromUnderscore).map(_._1)

    def expectedTypeEx(fromUnderscore: Boolean = true): Option[(ScType, Option[ScTypeElement])] =
      ExpectedTypes.expectedExprType(expr, fromUnderscore)

    def expectedTypes(fromUnderscore: Boolean = true): Array[ScType] = expectedTypesEx(fromUnderscore).map(_._1)

    @CachedWithRecursionGuard(expr, Array.empty[(ScType, Option[ScTypeElement])], ModCount.getBlockModificationCount)
    def expectedTypesEx(fromUnderscore: Boolean = true): Array[(ScType, Option[ScTypeElement])] = {
      ExpectedTypes.expectedExprTypes(expr, fromUnderscore = fromUnderscore)
    }

    @CachedWithRecursionGuard(expr, None, ModCount.getBlockModificationCount)
    def smartExpectedType(fromUnderscore: Boolean = true): Option[ScType] = ExpectedTypes.smartExpectedType(expr, fromUnderscore)

    def getTypeIgnoreBaseType: TypeResult[ScType] = getTypeAfterImplicitConversion(ignoreBaseTypes = true).tr

    @CachedWithRecursionGuard(expr, Failure("Recursive getNonValueType", Some(expr)), ModCount.getBlockModificationCount)
    def getNonValueType(ignoreBaseType: Boolean = false,
                        fromUnderscore: Boolean = false): TypeResult[ScType] = {
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
          Success(methType, Some(expr))
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
    @CachedWithRecursionGuard(expr, ExpressionTypeResult(Failure("Recursive getTypeAfterImplicitConversion", Some(expr))), ModCount.getBlockModificationCount)
    def getTypeAfterImplicitConversion(checkImplicits: Boolean = true,
                                       isShape: Boolean = false,
                                       expectedOption: Option[ScType] = None,
                                       ignoreBaseTypes: Boolean = false,
                                       fromUnderscore: Boolean = false): ExpressionTypeResult = {

      def isJavaReflectPolymorphic = expr.scalaLanguageLevelOrDefault >= Scala_2_11 && ScalaPsiUtil.isJavaReflectPolymorphicSignature(expr)

      val expected = expectedOption.orElse {
        expectedType(fromUnderscore = fromUnderscore)
      }

      if (isShape) ExpressionTypeResult(Success(shape(expr).getOrElse(Nothing), Some(expr)))
      else {
        val tr = getTypeWithoutImplicits(ignoreBaseTypes, fromUnderscore)
        (expected, tr.toOption) match {
          case (Some(expType), Some(tp))
            if checkImplicits && !tp.conforms(expType) => //do not try implicit conversions for shape check or already correct type

            val samType = tryConvertToSAM(fromUnderscore, expType, tp)

            if (samType.nonEmpty) samType.get
            else if (isJavaReflectPolymorphic) ExpressionTypeResult(Success(expType, Some(expr)))
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
                  ExpressionTypeResult(Success(mr, Some(expr)), result.importsUsed, Some(result.getElement))
                case _ =>
                  ExpressionTypeResult(tr)
              }
            }
          case _ => ExpressionTypeResult(tr)
        }
      }
    }

    @CachedWithRecursionGuard(expr, Failure("Recursive getTypeWithoutImplicits", Some(expr)),
      ModCount.getBlockModificationCount)
    def getTypeWithoutImplicits(ignoreBaseTypes: Boolean = false, fromUnderscore: Boolean = false): TypeResult[ScType] = {
      ProgressManager.checkCanceled()
      val fromNullLiteral = expr match {
        case lit: ScLiteral =>
          val typeForNull = lit.getTypeForNullWithoutImplicits
          if (typeForNull.nonEmpty) Option(Success(typeForNull.get, None))
          else None
        case _ => None
      }

      if (fromNullLiteral.nonEmpty) fromNullLiteral.get
      else {
        val inner = expr.getNonValueType(ignoreBaseTypes, fromUnderscore)
        inner match {
          case Success(rtp, _) =>
            var res = rtp

            val expType = expectedType(fromUnderscore)

            def updateExpected(oldRes: ScType): ScType = {
              try {
                val updatedWithExpected =
                  InferUtil.updateAccordingToExpectedType(Success(rtp, Some(expr)), fromImplicitParameters = true,
                    filterTypeParams = false, expectedType = expType, expr = expr, check = true)
                updatedWithExpected match {
                  case Success(newRes, _) =>
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
              expr.getParent match {
                case _: ScGenericCall => // all the implicits belong to the parent ScGenericCall
                case _                => res = updateExpected(rtp)
              }
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

            if (ignoreBaseTypes) Success(valType, Some(expr))
            else {
              expType match {
                case None =>
                  Success(valType, Some(expr))
                case Some(expected) if expected.removeAbstracts equiv Unit =>
                  //value discarding
                  Success(Unit, Some(expr))
                case Some(expected) =>
                  val narrowing = isNarrowing(expected)
                  if (narrowing.isDefined) narrowing.get
                  else {
                    val widening = isWidening(valType, expected)
                    if (widening.isDefined) widening.get
                    else Success(valType, Some(expr))
                  }
              }
            }
          case _ => inner
        }
      }
    }


    @CachedWithRecursionGuard(expr, Array.empty[ScalaResolveResult], ModCount.getBlockModificationCount)
    def applyShapeResolveForExpectedType(tp: ScType, exprs: Seq[ScExpression], call: Option[MethodInvocation]): Array[ScalaResolveResult] = {
      val applyProc =
        new MethodResolveProcessor(expr, "apply", List(exprs), Seq.empty, Seq.empty /* todo: ? */ ,
          StdKinds.methodsOnly, isShapeResolve = true)
      applyProc.processType(tp, expr)
      var cand = applyProc.candidates
      if (cand.length == 0 && call.isDefined) {
        val expr = call.get.getEffectiveInvokedExpr
        ScalaPsiUtil.findImplicitConversion(expr, "apply", expr, applyProc, noImplicitsForArgs = false).foreach { result =>
          val builder = new ImplicitResolveResult.ResolverStateBuilder(result).withImplicitFunction
          applyProc.processType(result.typeWithDependentSubstitutor, expr, builder.state)
          cand = applyProc.candidates
        }
      }
      if (cand.length == 0 && ScalaPsiUtil.approveDynamic(tp, expr.getProject, expr.resolveScope) && call.isDefined) {
        cand = ScalaPsiUtil.processTypeForUpdateOrApplyCandidates(call.get, tp, isShape = true, isDynamic = true)
      }
      cand
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
    def isNarrowing(expected: ScType): Option[TypeResult[ScType]] = {
      import expr.projectContext

      def isByte(v: Long)  = v >= scala.Byte.MinValue  && v <= scala.Byte.MaxValue
      def isChar(v: Long)  = v >= scala.Char.MinValue  && v <= scala.Char.MaxValue
      def isShort(v: Long) = v >= scala.Short.MinValue && v <= scala.Short.MaxValue

      def success(t: ScType) = Some(Success(t, Some(expr)))

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
        case Char  if isChar(intLiteralValue)  => success(Char)
        case Byte  if isByte(intLiteralValue)  => success(Byte)
        case Short if isShort(intLiteralValue) => success(Short)
        case _ => None
      }
    }

    //numeric widening
    private def isWidening(valueType: ScType, expected: ScType): Option[TypeResult[ScType]] = {
      val (l, r) = (getStdType(valueType), getStdType(expected)) match {
        case (Some(left), Some(right)) => (left, right)
        case _ => return None
      }
      val stdTypes = project.stdTypes
      import stdTypes._

      (l, r) match {
        case (Byte, Short | Int | Long | Float | Double) => Some(Success(expected, Some(expr)))
        case (Short, Int | Long | Float | Double) => Some(Success(expected, Some(expr)))
        case (Char, Byte | Short | Int | Long | Float | Double) => Some(Success(expected, Some(expr)))
        case (Int, Long | Float | Double) => Some(Success(expected, Some(expr)))
        case (Long, Float | Double) => Some(Success(expected, Some(expr)))
        case (Float, Double) => Some(Success(expected, Some(expr)))
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
        def expectedResult = Some(ExpressionTypeResult(Success(expected, Some(expr))))

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
