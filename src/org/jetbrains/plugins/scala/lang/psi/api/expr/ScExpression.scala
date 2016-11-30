package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.{ElementText, StringExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{MethodValue, isAnonymousExpression}
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.{SafeCheckException, extractImplicitParameterType}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.implicits.{ImplicitCollector, ImplicitResolveResult, ScImplicitlyConvertible}
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{api, _}
import org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, StdKinds}
import org.jetbrains.plugins.scala.macroAnnotations.{CachedMappedWithRecursionGuard, ModCount}
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
  /**
   * This method returns real type, after using implicit conversions.
   * Second parameter to return is used imports for this conversion.
   *
   * @param expectedOption to which type we trying to convert
   * @param ignoreBaseTypes parameter to avoid value discarding, literal narrowing, widening
   *                        this parameter is useful for refactorings (introduce variable)
   */
  @CachedMappedWithRecursionGuard(this, ExpressionTypeResult(Failure("Recursive getTypeAfterImplicitConversion", Some(this))), ModCount.getBlockModificationCount)
  def getTypeAfterImplicitConversion(checkImplicits: Boolean = true,
                                     isShape: Boolean = false,
                                     expectedOption: Option[ScType] = None,
                                     ignoreBaseTypes: Boolean = false,
                                     fromUnderscore: Boolean = false): ExpressionTypeResult = {
    val expected = expectedOption.orElse {
      expectedType(fromUnderscore = fromUnderscore)
    }

    if (isShape) {
      val tp: ScType = shape(ScExpression.this).getOrElse(Nothing)

      expected.filter {
        !tp.conforms(_)
      }.flatMap {
        tryConvertToSAM(fromUnderscore, _, tp)
      }.getOrElse(ExpressionTypeResult(Success(tp, Some(ScExpression.this))))
    }
    else {
      val tr = getTypeWithoutImplicits(ignoreBaseTypes, fromUnderscore)

      val maybeResult: Option[(ScType, ScalaResolveResult)] = expected.filter { _ =>
        checkImplicits //do not try implicit conversions for shape check
      }.flatMap { expected =>
        tr.toOption.filter {
          !_.conforms(expected) // if this result is ok, we do not need to think about implicits
        }.flatMap { tp =>
          tryConvertToSAM(fromUnderscore, expected, tp) match {
            case Some(r) => return r
            case _ =>
          }

          val scalaVersion = ScExpression.this.scalaLanguageLevelOrDefault
          if (scalaVersion >= Scala_2_11 && ScalaPsiUtil.isJavaReflectPolymorphicSignature(ScExpression.this)) {
            return ExpressionTypeResult(Success(expected, Some(ScExpression.this)))
          }

          val functionType = FunctionType(expected, Seq(tp))(getProject, getResolveScope)
          new ImplicitCollector(ScExpression.this, functionType, functionType, None, isImplicitConversion = true).collect() match {
            case Seq(res) =>
              val `type` = extractImplicitParameterType(res) match {
                case FunctionType(rt, Seq(_)) => Some(rt)
                case paramType =>
                  function1Type.flatMap { funTp =>
                    val secondArg = funTp.typeArguments(1)
                    paramType.conforms(funTp, ScUndefinedSubstitutor())._2.getSubstitutor.map {
                      _.subst(secondArg)
                    }.filter {
                      !_.isInstanceOf[UndefinedType]
                    }
                  }
              }

              `type`.map {
                (_, res)
              }
            case _ => None
          }
        }
      }

      maybeResult.map {
        case (tp, result) =>
          ExpressionTypeResult(Success(tp, Some(ScExpression.this)), result.importsUsed, Some(result.getElement))
      }.getOrElse {
        ExpressionTypeResult(tr)
      }
    }
  }

  //  private def temp(maybeType: Option[ScType], expectedType: ScType, fromUnderscore: Boolean) =
  //    maybeType.filter {
  //      !_.conforms(expectedType)
  //    }.flatMap {
  //      tryConvertToSAM(fromUnderscore, expectedType, _)
  //    }

  private def function1Type: Option[ScParameterizedType] =
    Option(ScalaPsiManager.instance(getProject)).map {
      _.getCachedClass("scala.Function1", getResolveScope, ScalaPsiManager.ClassCategory.TYPE)
    }.collect {
      case function1: ScTrait =>
        val parameters = function1.typeParameters.map { tp =>
          UndefinedType(TypeParameterType(tp), 1)
        }
        ScParameterizedType(ScalaType.designator(function1), parameters)
    }.collect {
      case parameterizedType: ScParameterizedType => parameterizedType
    }

  private def tryConvertToSAM(fromUnderscore: Boolean, expected: ScType, tp: ScType) = {
    def checkForSAM(etaExpansionHappened: Boolean = false): Option[ExpressionTypeResult] = {
      def expectedResult = Some(ExpressionTypeResult(Success(expected, Some(this))))
      tp match {
        case FunctionType(_, params) if ScalaPsiUtil.isSAMEnabled(this) =>
          ScalaPsiUtil.toSAMType(expected, this) match {
            case Some(methodType) if tp.conforms(methodType) => expectedResult
            case Some(methodType@FunctionType(retTp, _)) if etaExpansionHappened && retTp.equiv(Unit) =>
              val newTp = FunctionType(Unit, params)(getProject, getResolveScope)
              if (newTp.conforms(methodType)) expectedResult
              else None
            case _ => None
          }
        case _ => None
      }
    }

    this match {
      case ScFunctionExpr(_, _) if fromUnderscore => checkForSAM()
      case _ if !fromUnderscore && ScalaPsiUtil.isAnonExpression(this) => checkForSAM()
      case MethodValue(method) if this.scalaLanguageLevelOrDefault == Scala_2_11 || method.getParameterList.getParametersCount > 0 =>
        checkForSAM(etaExpansionHappened = true)
      case _ => None
    }
  }

  @CachedMappedWithRecursionGuard(this, Failure("Recursive getTypeWithoutImplicits", Some(this)),
    ModCount.getBlockModificationCount)
  private def getTypeWithoutImplicitsImpl(ignoreBaseTypes: Boolean, fromUnderscore: Boolean): TypeResult[ScType] = {
    val inner = ScExpression.this.getNonValueType(TypingContext.empty, ignoreBaseTypes, fromUnderscore)
    inner match {
      case Success(rtp, _) =>
        var res = rtp

        def tryUpdateRes(checkExpectedType: Boolean) {
          if (checkExpectedType) {
            InferUtil.updateAccordingToExpectedType(Success(res, Some(ScExpression.this)), fromImplicitParameters = true,
              filterTypeParams = false, expectedType = expectedType(fromUnderscore), expr = ScExpression.this,
              check = checkExpectedType) match {
              case Success(newRes, _) => res = newRes
              case _ =>
            }
          }

          val checkImplicitParameters = ScalaPsiUtil.withEtaExpansion(ScExpression.this)
          if (checkImplicitParameters) {
            val tuple = InferUtil.updateTypeWithImplicitParameters(res, ScExpression.this, None, checkExpectedType, fullInfo = false)
            res = tuple._1
            if (fromUnderscore) implicitParametersFromUnder = tuple._2
            else implicitParameters = tuple._2
          }
        }

        @tailrec
        def isMethodInvocation(expr: ScExpression = ScExpression.this): Boolean = {
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
        if (!isMethodInvocation()) { //it is not updated according to expected type, let's do it
        val oldRes = res
          try {
            tryUpdateRes(checkExpectedType = true)
          } catch {
            case _: SafeCheckException =>
              res = oldRes
              tryUpdateRes(checkExpectedType = false)
          }
        }

        def removeMethodType(retType: ScType, updateType: ScType => ScType = t => t) {
          def updateRes(exp: Option[ScType]) {

            exp match {
              case Some(expected) =>
                expected.removeAbstracts match {
                  case FunctionType(_, _) =>
                  case expect if ScalaPsiUtil.isSAMEnabled(ScExpression.this) =>
                    val languageLevel = ScExpression.this.scalaLanguageLevelOrDefault
                    if (languageLevel != Scala_2_11 || ScalaPsiUtil.toSAMType(expect, ScExpression.this).isEmpty) {
                      res = updateType(retType)
                    }
                  case _ => res = updateType(retType)
                }
              case _ => res = updateType(retType)
            }
          }

          updateRes(expectedType(fromUnderscore))
        }

        res match {
          case ScTypePolymorphicType(ScMethodType(retType, params, _), tp) if params.isEmpty  &&
            !ScUnderScoreSectionUtil.isUnderscore(ScExpression.this) =>
            removeMethodType(retType, t => ScTypePolymorphicType(t, tp))
          case ScMethodType(retType, params, _) if params.isEmpty &&
            !ScUnderScoreSectionUtil.isUnderscore(ScExpression.this) =>
            removeMethodType(retType)
          case _ =>
        }

        val valType = res.inferValueType.unpackedType

        if (ignoreBaseTypes) Success(valType, Some(ScExpression.this))
        else {
          expectedType(fromUnderscore) match {
            case Some(expected) =>
              //value discarding
              if (expected.removeAbstracts equiv Unit) return Success(Unit, Some(ScExpression.this))
              //numeric literal narrowing
              val needsNarrowing = ScExpression.this match {
                case _: ScLiteral => getNode.getFirstChildNode.getElementType == ScalaTokenTypes.tINTEGER
                case p: ScPrefixExpr => p.operand match {
                  case l: ScLiteral =>
                    l.getNode.getFirstChildNode.getElementType == ScalaTokenTypes.tINTEGER &&
                      Set("+", "-").contains(p.operation.getText)
                  case _ => false
                }
                case _ => false
              }

              def checkNarrowing: Option[TypeResult[ScType]] = {
                try {
                  lazy val i = ScExpression.this match {
                    case l: ScLiteral    => l.getValue match {
                      case i: Integer => i.intValue
                      case _          => scala.Int.MaxValue
                    }
                    case p: ScPrefixExpr =>
                      val mult = if (p.operation.getText == "-") -1 else 1
                      p.operand match {
                        case l: ScLiteral => l.getValue match {
                          case i: Integer => mult * i.intValue
                          case _          => scala.Int.MaxValue
                        }
                      }
                  }
                  expected.removeAbstracts match {
                    case api.Char =>
                      if (i >= scala.Char.MinValue.toInt && i <= scala.Char.MaxValue.toInt) {
                        return Some(Success(Char, Some(ScExpression.this)))
                      }
                    case api.Byte =>
                      if (i >= scala.Byte.MinValue.toInt && i <= scala.Byte.MaxValue.toInt) {
                        return Some(Success(Byte, Some(ScExpression.this)))
                      }
                    case api.Short =>
                      if (i >= scala.Short.MinValue.toInt && i <= scala.Short.MaxValue.toInt) {
                        return Some(Success(Short, Some(ScExpression.this)))
                      }
                    case _           =>
                  }
                }
                catch {
                  case _: NumberFormatException => //do nothing
                }
                None
              }

              val check = if (needsNarrowing) checkNarrowing else None
              if (check.isDefined) check.get
              else {
                //numeric widening
                def checkWidening(l: ScType, r: ScType): Option[TypeResult[ScType]] = {
                  (l, r) match {
                    case (Byte, Short | Int | Long | Float | Double) => Some(Success(expected, Some(ScExpression.this)))
                    case (Short, Int | Long | Float | Double) => Some(Success(expected, Some(ScExpression.this)))
                    case (Char, Byte | Short | Int | Long | Float | Double) => Some(Success(expected, Some(ScExpression.this)))
                    case (Int, Long | Float | Double) => Some(Success(expected, Some(ScExpression.this)))
                    case (Long, Float | Double) => Some(Success(expected, Some(ScExpression.this)))
                    case (Float, Double) => Some(Success(expected, Some(ScExpression.this)))
                    case _ => None
                  }
                }

                def getValType: ScType => Option[ScType] = {
                  case AnyVal => Some(AnyVal)
                  case valType: ValType => Some(valType)
                  case designatorType: ScDesignatorType => designatorType.getValType
                  case _ => None
                }

                (getValType(valType), getValType(expected)) match {
                  case (Some(l), Some(r)) => checkWidening(l, r) match {
                    case Some(x) => x
                    case _ => Success(valType, Some(ScExpression.this))
                  }
                  case _ => Success(valType, Some(ScExpression.this))
                }
              }
            case _ => Success(valType, Some(ScExpression.this))
          }
        }
      case _ => inner
    }
  }

  def getTypeWithoutImplicits(ignoreBaseTypes: Boolean = false,
                              fromUnderscore: Boolean = false): TypeResult[ScType] = {
    ProgressManager.checkCanceled()
    getTypeWithoutImplicitsImpl(ignoreBaseTypes, fromUnderscore)
  }

  override def getType(ctx: TypingContext): TypeResult[ScType] = {
    this match {
      case ref: ScReferenceExpression if ref.refName == ScImplicitlyConvertible.IMPLICIT_EXPRESSION_NAME =>
        val data = getUserData(ScImplicitlyConvertible.FAKE_EXPRESSION_TYPE_KEY)
        if (data != null) return Success(data, Some(this))
      case _ =>
    }
    getTypeAfterImplicitConversion().tr
  }

  def getTypeIgnoreBaseType: TypeResult[ScType] = getTypeAfterImplicitConversion(ignoreBaseTypes = true).tr

  @volatile
  protected var implicitParameters: Option[Seq[ScalaResolveResult]] = None

  @volatile
  protected var implicitParametersFromUnder: Option[Seq[ScalaResolveResult]] = None

  /**
   * Warning! There is a hack in scala compiler for ClassManifest and ClassTag.
   * In case of implicit parameter with type ClassManifest[T]
   * this method will return ClassManifest with substitutor of type T.
 *
   * @return implicit parameters used for this expression
   */
  def findImplicitParameters: Option[Seq[ScalaResolveResult]] = {
    ProgressManager.checkCanceled()

    if (ScUnderScoreSectionUtil.underscores(this).nonEmpty) {
      getTypeWithoutImplicits(fromUnderscore = true) //to update implicitParametersFromUnder
      implicitParametersFromUnder
    } else {
      getType(TypingContext.empty) //to update implicitParameters field
      implicitParameters
    }
  }

  @CachedMappedWithRecursionGuard(this, Failure("Recursive getNonValueType", Some(this)), ModCount.getBlockModificationCount)
  private def getNonValueTypeImpl(ignoreBaseType: Boolean, fromUnderscore: Boolean): TypeResult[ScType] = {
    if (fromUnderscore) innerType(TypingContext.empty)
    else {
      val unders = ScUnderScoreSectionUtil.underscores(ScExpression.this)
      if (unders.isEmpty) innerType(TypingContext.empty)
      else {
        val params = unders.zipWithIndex.map {
          case (u, index) =>
            val tpe = u.getNonValueType(TypingContext.empty, ignoreBaseType).getOrAny.inferValueType.unpackedType
            new Parameter("", None, tpe, false, false, false, index)
        }
        val methType =
          ScMethodType(getTypeAfterImplicitConversion(ignoreBaseTypes = ignoreBaseType,
            fromUnderscore = true).tr.getOrAny,
            params, isImplicit = false)(getProject, getResolveScope)
        Success(methType, Some(ScExpression.this))
      }
    }
  }

  protected def innerType(ctx: TypingContext): TypeResult[ScType] =
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


  def expectedType(fromUnderscore: Boolean = true): Option[ScType] = {
    this match {
      case ref: ScMethodCall if ref.getText == ScImplicitlyConvertible.IMPLICIT_CALL_TEXT =>
        val data = getUserData(ScImplicitlyConvertible.FAKE_EXPECTED_TYPE_KEY)
        if (data != null) return data
      case _ =>
    }
    expectedTypeEx(fromUnderscore).map(_._1)
  }

  def expectedTypeEx(fromUnderscore: Boolean = true): Option[(ScType, Option[ScTypeElement])] =
    ExpectedTypes.expectedExprType(this, fromUnderscore)

  def expectedTypes(fromUnderscore: Boolean = true): Array[ScType] = expectedTypesEx(fromUnderscore).map(_._1)

  @CachedMappedWithRecursionGuard(this, Array.empty[(ScType, Option[ScTypeElement])], ModCount.getBlockModificationCount)
  def expectedTypesEx(fromUnderscore: Boolean = true): Array[(ScType, Option[ScTypeElement])] = {
    ExpectedTypes.expectedExprTypes(ScExpression.this, fromUnderscore = fromUnderscore)
  }

  @CachedMappedWithRecursionGuard(this, None, ModCount.getBlockModificationCount)
  def smartExpectedType(fromUnderscore: Boolean = true): Option[ScType] = ExpectedTypes.smartExpectedType(ScExpression.this, fromUnderscore)

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

  /**
    * This method returns following values:
    *
    * @return implicit conversions, actual value, conversions from the first part, conversions from the second part
    */
  def getImplicitConversions(fromUnderscore: Boolean = false,
                             expectedOption: => Option[ScType] = smartExpectedType()):
  (Seq[PsiNamedElement], Option[PsiNamedElement], Seq[PsiNamedElement], Seq[PsiNamedElement]) = {
    val (regularResults, companionResults) = new ScImplicitlyConvertible(this, fromUnderscore)
      .implicitMap(arguments = expectedTypes(fromUnderscore).toSeq)

    def referenceImplicitFunction(reference: ScReferenceExpression) = reference.multiResolve(false) match {
      case Array(result: ScalaResolveResult) => result.implicitFunction
      case _ => None
    }

    def implicitFunction(element: ScalaPsiElement): Option[PsiNamedElement] = element.getParent match {
      case reference: ScReferenceExpression =>
        referenceImplicitFunction(reference)
      case expression@ScInfixExpr(leftOperand, operation, rightOperand)
        if (expression.isLeftAssoc && this == rightOperand) || (!expression.isLeftAssoc && this == leftOperand) =>
        referenceImplicitFunction(operation)
      case call: ScMethodCall => call.getImplicitFunction
      case generator: ScGenerator => implicitFunction(generator)
      case _ =>
        getTypeAfterImplicitConversion(expectedOption = expectedOption, fromUnderscore = fromUnderscore).implicitFunction
    }

    val regularElements = regularResults.map(_.element)
    val companionElements = companionResults.map(_.element)
    (regularElements ++ companionElements, implicitFunction(this), regularElements, companionElements)
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

  @CachedMappedWithRecursionGuard(this, Array.empty[ScalaResolveResult], ModCount.getBlockModificationCount)
  def applyShapeResolveForExpectedType(tp: ScType, exprs: Seq[ScExpression], call: Option[MethodInvocation]): Array[ScalaResolveResult] = {
    val applyProc =
      new MethodResolveProcessor(ScExpression.this, "apply", List(exprs), Seq.empty, Seq.empty /* todo: ? */,
        StdKinds.methodsOnly, isShapeResolve = true)
    applyProc.processType(tp, ScExpression.this)
    var cand = applyProc.candidates
    if (cand.length == 0 && call.isDefined) {
      val expr = call.get.getEffectiveInvokedExpr
      ScalaPsiUtil.findImplicitConversion(expr, "apply", expr, applyProc, noImplicitsForArgs = false).foreach { result =>
        val builder = new ImplicitResolveResult.ResolverStateBuilder(result).withImplicitFunction
        applyProc.processType(result.getTypeWithDependentSubstitutor, expr, builder.state)
        cand = applyProc.candidates
      }
    }
    if (cand.length == 0 && ScalaPsiUtil.approveDynamic(tp, getProject, getResolveScope) && call.isDefined) {
      cand = ScalaPsiUtil.processTypeForUpdateOrApplyCandidates(call.get, tp, isShape = true, isDynamic = true)
    }
    cand
  }
}

object ScExpression {
  case class ExpressionTypeResult(tr: TypeResult[ScType],
                                  importsUsed: scala.collection.Set[ImportUsed] = Set.empty,
                                  implicitFunction: Option[PsiNamedElement] = None)

  object Type {
    def unapply(exp: ScExpression): Option[ScType] = exp.getType(TypingContext.empty).toOption
  }

  implicit class ScExpressionExt(val expr: ScExpression) extends AnyVal {
    def getNonValueType(ctx: TypingContext = TypingContext.empty, //todo: remove?
                        ignoreBaseType: Boolean = false,
                        fromUnderscore: Boolean = false): TypeResult[ScType] = {
      ProgressManager.checkCanceled()
      expr.getNonValueTypeImpl(ignoreBaseType, fromUnderscore)
    }
  }

  private def shape(expression: ScExpression, ignoreAssign: Boolean = false): Option[ScType] = {
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
            FunctionType(maybeResultType.getOrElse(Nothing), argumentsTypes)(expression.getProject, expression.getResolveScope)
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
