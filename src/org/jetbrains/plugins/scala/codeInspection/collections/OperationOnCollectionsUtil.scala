package org.jetbrains.plugins.scala
package codeInspection.collections

import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiType, PsiMethod}
import org.jetbrains.plugins.scala.codeInspection.collections.OperationOnCollectionsUtil._
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaEvaluatorBuilderUtil
import org.jetbrains.plugins.scala.extensions.{ExpressionType, ResolvesTo, childOf}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReferenceElement, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScValue, ScVariable, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiUtil, types}
import org.jetbrains.plugins.scala.lang.psi.types.ScFunctionType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.language.implicitConversions

/**
 * Nikolay.Tropin
 * 5/20/13
 */
class MethodRepr private (val itself: ScExpression,
                               val optionalBase: Option[ScExpression],
                               val optionalMethodRef: Option[ScReferenceExpression],
                               val args: Seq[ScExpression]) {

  def rightRangeInParent(parent: ScExpression): TextRange = {
    val endOffset = parent.getTextRange.getEndOffset

    val startOffset = optionalMethodRef match {
      case Some(ref) =>
       ref.nameId.getTextOffset
      case None =>
        optionalBase match {
          case Some(b) => b.getTextRange.getEndOffset
          case _ => parent.getTextOffset
        }
    }
    TextRange.create(startOffset, endOffset).shiftRight( - parent.getTextOffset)
  }
}

object MethodRepr {
  //method represented by optional base expression, optional method reference and arguments
  def unapply(expr: ScExpression): Option[(ScExpression, Option[ScExpression], Option[ScReferenceExpression], Seq[ScExpression])] = {
    expr match {
      case call: ScMethodCall =>
        val args = call.args match {
          case exprList: ScArgumentExprList => exprList.exprs.map(stripped)
          case _ => Nil
        }
        call.getEffectiveInvokedExpr match {
          case baseExpr: ScExpression if call.isApplyOrUpdateCall && !call.isUpdateCall =>
            Some(expr, Some(baseExpr), None, args)
          case ref: ScReferenceExpression => Some(expr, ref.qualifier, Some(ref), args)
          case genericCall: ScGenericCall =>
            genericCall.referencedExpr match {
              case ref: ScReferenceExpression => Some(expr, ref.qualifier, Some(ref), args)
              case other => Some(expr, None, None, args)
            }
          case methCall: ScMethodCall => Some(expr, Some(methCall), None, args)
          case other => Some(expr, None, None, args)
        }
      case infix: ScInfixExpr =>
        val args = infix.getArgExpr match {
          case tuple: ScTuple => tuple.exprs
          case _ => Seq(infix.getArgExpr)
        }
        Some(expr, Some(stripped(infix.getBaseExpr)), Some(infix.operation), args)
      case prefix: ScPrefixExpr => Some(expr, Some(stripped(prefix.getBaseExpr)), Some(prefix.operation), Seq())
      case postfix: ScPostfixExpr => Some(expr, Some(stripped(postfix.getBaseExpr)), Some(postfix.operation), Seq())
      case refExpr: ScReferenceExpression =>
        refExpr.getParent match {
          case _: ScMethodCall | _: ScGenericCall => None
          case ScInfixExpr(_, `refExpr`, _) => None
          case ScPostfixExpr(_, `refExpr`) => None
          case ScPrefixExpr(`refExpr`, _) => None
          case _ => Some(expr, refExpr.qualifier, Some(refExpr), Seq())
        }
      case genCall: ScGenericCall =>
        genCall.getParent match {
          case _: ScMethodCall => None
          case _ => genCall.referencedExpr match {
            case ref: ScReferenceExpression => Some(genCall, ref.qualifier, Some(ref), Seq.empty)
            case other => Some(genCall, None, None, Seq.empty)
          }
        }
      case _ => None
    }
  }

  def apply(itself: ScExpression, optionalBase: Option[ScExpression], optionalMethodRef: Option[ScReferenceExpression], args: Seq[ScExpression]) = {
    new MethodRepr(itself, optionalBase, optionalMethodRef, args)
  }

}

object MethodSeq {
  def unapplySeq(expr: ScExpression): Option[Seq[MethodRepr]] = {
    val result = ArrayBuffer[MethodRepr]()
    @tailrec
    def extractMethods(expr: ScExpression) {
      expr match {
        case MethodRepr(itself, optionalBase, optionalMethodRef, args) =>
          result += MethodRepr(expr, optionalBase, optionalMethodRef, args)
          optionalBase match {
            case Some(ScParenthesisedExpr(inner)) => extractMethods(stripped(inner))
            case Some(expression) => extractMethods(expression)
            case _ =>
          }
        case _ =>
      }
    }
    extractMethods(expr)
    if (result.length > 0) Some(result) else None
  }
}

object OperationOnCollectionsUtil {

  val foldMethodNames = Seq("foldLeft", "/:", "foldRight", ":\\", "fold")
  val reduceMethodNames = Seq("reduce", "reduceLeft", "reduceRight")

  def checkHasImplicitParameterFor(methodName: String, baseExpr: Option[ScExpression]): Boolean = {
    baseExpr match {
      case None => false
      case Some(e) =>
        val sumExpr = ScalaPsiElementFactory.createExpressionWithContextFromText(s"${e.getText}.$methodName", e.getContext, e)
        sumExpr.findImplicitParameters match {
          case Some(Seq(srr: ScalaResolveResult, _*)) => true
          case _ => false
        }
    }
  }

  @tailrec
  def stripped(expr: ScExpression): ScExpression = {
    expr match {
      case ScParenthesisedExpr(inner) => stripped(inner)
      case block: ScBlock if block.statements.size == 1 =>
        block.statements(0) match {
          case inner: ScExpression => stripped(inner)
          case _ => block
        }
      case _ => expr
    }
  }

  def isFunctionWithBooleanReturn(expr: ScExpression): Boolean = {
    expr.getType(TypingContext.empty) match {
      case Success(result, _) =>
        result match {
          case ScFunctionType(returnType, _) => returnType.conforms(types.Boolean)
          case _ => false
        }
      case _ => false
    }
  }

  def isLiteral(args: Seq[ScExpression], text: String): Boolean = {
    args.size == 1 && args(0).isInstanceOf[ScLiteral] && args(0).getText == text
  }

  //@tailrec
  def isBinaryOp(expr: ScExpression, opName: String): Boolean = {
    stripped(expr) match {
      case ScFunctionExpr(Seq(x, y), Some(result)) =>
        def checkResolve(left: ScExpression, right: ScExpression) = (stripped(left), stripped(right)) match {
          case (leftRef: ScReferenceExpression, rightRef: ScReferenceExpression) =>
            Set(leftRef.resolve(), rightRef.resolve()) equals Set(x, y)
          case _ => false
        }
        stripped(result) match {
          case ScInfixExpr(left, oper, right) if oper.refName == opName =>
            checkResolve(left, right)
          case ScMethodCall(refExpr: ScReferenceExpression, Seq(left, right))
            if refExpr.refName == opName =>
            checkResolve(left, right)
          case _ => false
        }
      case ScInfixExpr(left, oper, right) if oper.refName == opName =>
        isUndescore(stripped(left)) && isUndescore(stripped(right))
      case ScMethodCall(refExpr: ScReferenceExpression, Seq(left, right))
        if refExpr.refName == opName =>
        isUndescore(stripped(left)) && isUndescore(stripped(right))
      case _ => false
    }
  }

  def isIndependentOf(expr: ScExpression, parameter: ScParameter): Boolean = {
    var result = true
    val name = parameter.getName
    val visitor = new ScalaRecursiveElementVisitor() {
      override def visitReferenceExpression(ref: ScReferenceExpression) {
        if (ref.refName == name && ref.resolve() == parameter) result = false
        super.visitReferenceExpression(ref)
      }
    }
    expr.accept(visitor)
    result
  }
  
  def andWithSomeFunction(expr: ScExpression): Option[ScExpression] = {
    stripped(expr) match {
      case ScFunctionExpr(Seq(x, y), Some(result)) =>
        stripped(result) match {
          case ScInfixExpr(left, oper, right) if oper.refName == "&&" =>
            (stripped(left), stripped(right)) match {
              case (leftRef: ScReferenceExpression, right: ScExpression)
                if leftRef.resolve() == x && isIndependentOf(right, x) =>
                val secondArgName = y.getName
                val funExprText = secondArgName + " => " + right.getText
                Some(ScalaPsiElementFactory.createExpressionWithContextFromText(funExprText, expr.getContext, expr))
              case _ => None
            }
          case _ => None
        }
      case ScInfixExpr(left, oper, right) if oper.refName == "&&" && isUndescore(left) => Some(right)
      case _ => None
    }
  }
  
  def isEqualsWithSomeExpr(expr: ScExpression): Option[ScExpression] = {
    stripped(expr) match {
      case ScFunctionExpr(Seq(x), Some(result)) =>
        stripped(result) match {
          case ScInfixExpr(left, oper, right) if oper.refName == "==" =>
            (stripped(left), stripped(right)) match {
              case (leftRef: ScReferenceExpression, rightExpr)
                if leftRef.resolve() == x && isIndependentOf(rightExpr, x) =>
                Some(rightExpr)
              case (leftExpr: ScExpression, rightRef: ScReferenceExpression)
                if rightRef.resolve() == x && isIndependentOf(leftExpr, x) => 
                Some(leftExpr)
              case _ => None
            }
          case _ => None
        }
      case ScInfixExpr(left, oper, right) if oper.refName == "==" && isUndescore(left) => Some(right)
      case ScInfixExpr(left, oper, right) if oper.refName == "==" && isUndescore(right) => Some(left)
      case _ => None
    }
  }

  implicit def scExprToString(expr: ScExpression): String = {
    expr.getText
  }

  @tailrec
  private def isUndescore(expr: ScExpression): Boolean = { //todo: more or less duplicate of ScUnderscoreSectionUtil.isUnderscore
    stripped(expr) match {
      case ScParenthesisedExpr(inner) => isUndescore(inner)
      case typed: ScTypedStmt if typed.expr.isInstanceOf[ScUnderscoreSection] => true
      case und: ScUnderscoreSection => true
      case _ => false
    }
  }

  def checkResolve(expr: ScExpression, patterns: Array[String]): Boolean = {
    expr match {
      case ref: ScReferenceExpression =>
        import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings.nameFitToPatterns
        ref.resolve() match {
          case obj: ScObject =>
            nameFitToPatterns(obj.qualifiedName, patterns)
          case member: ScMember =>
            val clazz = member.containingClass
            if (clazz == null) false
            else nameFitToPatterns(clazz.qualifiedName, patterns)
          case _ => false
        }
      case _ => false
    }
  }

  private val sideEffectsCollectionMethods = Set("append", "appendAll", "clear", "insert", "insertAll",
    "prepend", "prependAll", "reduceToSize", "remove", "retain",
    "transform", "trimEnd", "trimStart", "update",
    "push", "pushAll", "pop", "dequeue", "dequeueAll", "dequeueFirst", "enqueue",
    "next")

  def exprsWithSideEffect(expr: ScExpression): Iterator[ScExpression] = {

    def isSideEffectCollectionMethod(ref: ScReferenceExpression): Boolean = {
      val refName = ref.refName
      (refName.endsWith("=") || refName.endsWith("=:") || sideEffectsCollectionMethods.contains(refName)) &&
              OperationOnCollectionsUtil.checkResolve(ref, Array("scala.collection.mutable._", "scala.collection.Iterator"))
    }

    def isSetter(ref: ScReferenceExpression): Boolean = {
      ref.refName.startsWith("set") || ref.refName.endsWith("_=")
    }

    def hasUnitReturnType(ref: ScReferenceExpression): Boolean = {
      ref match {
        case MethodRepr(ExpressionType(ScFunctionType(_, _)), _, _, _) => false
        case ResolvesTo(fun: ScFunction) => fun.hasUnitResultType
        case ResolvesTo(m: PsiMethod) => m.getReturnType == PsiType.VOID
        case _ => false
      }
    }

    object definedOutside {
      def unapply(ref: ScReferenceElement): Option[PsiElement] = ref match {
        case ResolvesTo(elem: PsiElement) if !PsiTreeUtil.isAncestor(expr, elem, false) => Some(elem)
        case _ => None
      }
    }

    val predicate: (PsiElement) => Boolean = {
      case `expr` => true
      case ScFunctionExpr(_, _) childOf `expr` => true
      case (e: ScExpression) childOf `expr` if ScUnderScoreSectionUtil.underscores(e).nonEmpty => true
      case fun: ScFunctionDefinition => false
      case elem: PsiElement => !ScalaEvaluatorBuilderUtil.isGenerateClass(elem)
    }

    val sameLevelIterator = expr.depthFirst(predicate).filter(predicate)

    sameLevelIterator.collect {
      case assign @ ScAssignStmt(definedOutside(ScalaPsiUtil.inNameContext(_: ScVariable)), _) =>
        assign
      case assign @ ScAssignStmt(mc @ ScMethodCall(definedOutside(_), _), _) if mc.isUpdateCall =>
        assign
      case infix @ ScInfixExpr(definedOutside(ScalaPsiUtil.inNameContext(v: ScVariable)), _, _) if infix.isAssignmentOperator =>
        infix
      case MethodRepr(itself, Some(definedOutside(ScalaPsiUtil.inNameContext(v @ (_ : ScVariable | _: ScValue)))), Some(ref), _)
        if isSideEffectCollectionMethod(ref) || isSetter(ref) || hasUnitReturnType(ref) => itself
      case MethodRepr(itself, None, Some(ref @ definedOutside(_)), _) if hasUnitReturnType(ref) => itself
    }
  }

}