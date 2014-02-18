package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.lang.psi.api.expr._
import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.{types, ScalaPsiUtil}
import Utils._
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScFunctionType}
import scala.Some
import org.jetbrains.plugins.scala.lang.psi.types.result.Success
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import lang.psi.api.expr.ScTuple
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScMember}
import scala.annotation.tailrec

/**
 * Nikolay.Tropin
 * 5/20/13
 */
class MethodRepr private (val itself: ScExpression,
                               val optionalBase: Option[ScExpression],
                               val optionalMethodRef: Option[ScReferenceExpression],
                               val args: Seq[ScExpression]) {

  def rightRangeInParent(parent: ScExpression): TextRange = {
    optionalMethodRef match {
      case Some(ref) =>
        val startOffset = ref.nameId.getTextOffset
        val endOffset = parent.getTextRange.getEndOffset
        TextRange.create(startOffset, endOffset).shiftRight( - parent.getTextOffset)
      case None => TextRange.create(0, parent.getTextLength)
    }
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
          case _ => Some(expr, refExpr.qualifier, Some(refExpr), Seq())
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

object Utils {
  @tailrec
  def stripped(expr: ScExpression): ScExpression = {
    expr match {
      case ScParenthesisedExpr(inner) =>
        if (ScalaPsiUtil.needParentheses(expr, inner)) expr
        else stripped(inner)
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
  def isSum(expr: ScExpression): Boolean = {
    stripped(expr) match {
      case ScFunctionExpr(Seq(x, y), Some(result)) =>
        stripped(result) match {
          case ScInfixExpr(left, oper, right) if oper.refName == "+" =>
            (stripped(left), stripped(right)) match {
              case (leftRef: ScReferenceExpression, rightRef: ScReferenceExpression) =>
                Set(leftRef.resolve(), rightRef.resolve()) equals Set(x, y)
              case _ => false
            }
          case _ => false
        }
      case ScInfixExpr(left, oper, right) if oper.refName == "+" =>
        isUndescore(stripped(left)) && isUndescore(stripped(right))
      case _ => false
    }
  }

  def andWithSomeFunction(expr: ScExpression): Option[String] = {
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

    stripped(expr) match {
      case ScFunctionExpr(Seq(x, y), Some(result)) =>
        stripped(result) match {
          case ScInfixExpr(left, oper, right) if oper.refName == "&&" =>
            (stripped(left), stripped(right)) match {
              case (leftRef: ScReferenceExpression, right: ScExpression)
                if leftRef.resolve() == x && isIndependentOf(right, x) =>
                val secondArgName = y.getName
                Some(secondArgName + " => " + right.getText)
              case _ => None
            }
          case _ => None
        }
      case ScInfixExpr(left, oper, right) if oper.refName == "&&" && isUndescore(left) => Some(right)
      case _ => None
    }
  }

  implicit def scExprToString(expr: ScExpression): String = {
    expr.getText
  }

  @tailrec
  private def isUndescore(expr: ScExpression): Boolean = {
    stripped(expr) match {
      case ScParenthesisedExpr(inner) => isUndescore(inner)
      case typed: ScTypedStmt if typed.expr.isInstanceOf[ScUnderscoreSection] => true
      case und: ScUnderscoreSection => true
      case _ => false
    }
  }

  def checkResolve(expr: ScExpression, patterns: Array[String]): Boolean = {
//    if (ApplicationManager.getApplication.isUnitTestMode) true
//    else expr match {
//      case ref: ScReferenceExpression =>
//        import org.jetbrains.plugins.scala.settings.ScalaProjectSettings.nameFitToPatterns
//        ref.resolve() match {
//          case obj: ScObject =>
//            nameFitToPatterns(obj.qualifiedName, patterns)
//          case member: ScMember =>
//            val className = member.containingClass.qualifiedName
//            nameFitToPatterns(className, patterns)
//          case _ => false
//        }
//      case _ => false
//    }
    expr match {
      case ref: ScReferenceExpression =>
        import org.jetbrains.plugins.scala.settings.ScalaProjectSettings.nameFitToPatterns
        ref.resolve() match {
          case obj: ScObject =>
            nameFitToPatterns(obj.qualifiedName, patterns)
          case member: ScMember =>
            val className = member.containingClass.qualifiedName
            nameFitToPatterns(className, patterns)
          case _ => false
        }
      case _ => false
    }
  }
}