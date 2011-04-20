package org.jetbrains.plugins.scala
package lang
package resolve

import _root_.com.intellij.psi.impl.source.resolve.ResolveCache
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import processor.MethodResolveProcessor
import psi.types.Compatibility.Expression
import psi.types.Compatibility.Expression._
import com.intellij.psi.{ResolveResult, PsiElement}
import psi.api.statements.ScFunction
import psi.types.{Equivalence, ScParameterizedType, ScFunctionType, ScType}

class ReferenceExpressionResolver(reference: ResolvableReferenceExpression, shapesOnly: Boolean) 
        extends ResolveCache.PolyVariantResolver[ResolvableReferenceExpression] {
  case class ContextInfo(arguments: Option[Seq[Expression]], expectedType: () => Option[ScType], isUnderscore: Boolean)
  
  private def argumentsOf(ref: PsiElement): Seq[Expression] = {
    ref.getContext match {
      case infixExpr: ScInfixExpr => {
        //TODO should rOp really be parsed as Tuple (not as argument list)?
        infixExpr.rOp match {
          case t: ScTuple => t.exprs
          case op => Seq(op)
        }
      }
      case methodCall: ScMethodCall => methodCall.argumentExpressions
    }
  }

  private def getContextInfo(ref: ResolvableReferenceExpression, e: ScExpression): ContextInfo = {
    e.getContext match {
      case generic : ScGenericCall => getContextInfo(ref, generic)
      case call: ScMethodCall if !call.isUpdateCall => ContextInfo(Some(call.argumentExpressions), () => None, false)
      case call: ScMethodCall => ContextInfo(None, () => None, false)
      case section: ScUnderscoreSection => ContextInfo(None, () => section.expectedType, true)
      case inf: ScInfixExpr if ref == inf.operation => {
        ContextInfo(if (ref.rightAssoc) Some(Seq(inf.lOp)) else inf.rOp match {
          case tuple: ScTuple => Some(tuple.exprs)
          case rOp => Some(Seq(rOp))
        }, () => None, false)
      }
      case parents: ScParenthesisedExpr => getContextInfo(ref, parents)
      case postf: ScPostfixExpr if ref == postf.operation => getContextInfo(ref, postf)
      case pref: ScPrefixExpr if ref == pref.operation => getContextInfo(ref, pref)
      case _ => ContextInfo(None, () => e.expectedType, false)
    }
  }

  private def kinds(ref: ResolvableReferenceExpression, e: ScExpression, incomplete: Boolean): scala.collection.Set[ResolveTargets.Value] = {
    e.getContext match {
      case gen: ScGenericCall => kinds(ref, gen, incomplete)
      case parents: ScParenthesisedExpr => kinds(ref, parents, incomplete)
      case _: ScMethodCall | _: ScUnderscoreSection => StdKinds.methodRef
      case inf: ScInfixExpr if ref == inf.operation => StdKinds.methodRef
      case postf: ScPostfixExpr if ref == postf.operation => StdKinds.methodRef
      case pref: ScPrefixExpr if ref == pref.operation => StdKinds.methodRef
      case _ => reference.getKinds(incomplete)
    }
  }

  private def getTypeArgs(e : ScExpression) : Seq[ScTypeElement] = {
    e.getContext match {
      case generic: ScGenericCall => generic.arguments
      case parents: ScParenthesisedExpr => getTypeArgs(parents)
      case _ => Seq.empty
    }
  }

  def resolve(ref: ResolvableReferenceExpression, incomplete: Boolean): Array[ResolveResult] = {
    val name = if(ref.isUnaryOperator) "unary_" + reference.refName else reference.refName

    val info = getContextInfo(ref, ref)

    //expectedOption different for cases
    // val a: (Int) => Int = foo
    // and for case
    // val a: (Int) => Int = _.foo
    val expectedOption = {
      ref.getText.indexOf("_") match {
        case -1 => info.expectedType.apply //optimization
        case _ => {
          val unders = ScUnderScoreSectionUtil.underscores(ref)
          if (unders.length != 0) {
            info.expectedType.apply match {
              case Some(ScFunctionType(ret, _)) => Some(ret)
              case Some(p: ScParameterizedType) if p.getFunctionType != None => Some(p.typeArgs.last)
              case x => x
            }
          } else info.expectedType.apply
        }
      }
    }

    ref.getContext match {
      case assign: ScAssignStmt if assign.getLExpression == ref
              && !ref.getContext.getContext.isInstanceOf[ScArgumentExprList] =>
        // SLS 6.1.5 "The interpretation of an assignment to a simple variable x = e depends on the definition of x."

        // If x denotes a mutable variable, then the assignment changes the current value of x to be the result of evaluating the expression e
        val processor = new MethodResolveProcessor(ref, name, info.arguments.toList,
          getTypeArgs(ref), StdKinds.varsRef, () => None, info.isUnderscore, shapesOnly)
        val result = reference.doResolve(ref, processor)

        
        if (result.nonEmpty) result
        else searchForSetter(ref, assign, info.isUnderscore)
      case _ =>
        val processor = new MethodResolveProcessor(ref, name, info.arguments.toList,
          getTypeArgs(ref), kinds(ref, ref, incomplete), () => expectedOption, info.isUnderscore,
          shapesOnly, enableTupling = true)

        val result = reference.doResolve(ref, processor)
        if (result.isEmpty && ref.isAssignmentOperator) {
          val result1: Array[ResolveResult] = reference.doResolve(ref, new MethodResolveProcessor(ref, reference.refName.init, List(argumentsOf(ref)),
            Nil, isShapeResolve = shapesOnly, enableTupling = true))
          result1.map(r => r.asInstanceOf[ScalaResolveResult].copy(isAssignment = true): ResolveResult)
        } else {
          result
        }
    }
  }

  // See SCL-2868
  // "If x is a parameterless function defined in some template, and the same template contains a setter function x_= as member,
  // then the assignment x = e is interpreted as the invocation x_=(e ) of that setter function.
  // Analogously, an assignment f.x = e to a parameterless function x is interpreted as the invocation f.x_=(e).
  def searchForSetter(ref: ResolvableReferenceExpression, assign: ScAssignStmt, isUnderscore: Boolean) = {
    val getterResults = reference.doResolve(ref, new MethodResolveProcessor(ref, reference.refName, Nil, Nil, kinds = StdKinds.methodsOnly))

    val args = List(assign.getRExpression.toList.map(Expression(_)))
    val setterName = reference.refName + "_="
    val setterResults = reference.doResolve(ref, new MethodResolveProcessor(ref, setterName, args, Nil,
      kinds = StdKinds.methodsOnly, isUnderscore = isUnderscore, isShapeResolve = shapesOnly, enableTupling = true))

    val r1 = setterResults.map(x => x.asInstanceOf[ScalaResolveResult].copy(isSetterFunction = true): ResolveResult)

    // Don't resolve to the setter unless there is also a getter defined in the same template.
    r1.filter {
      r =>
        val sr = r.asInstanceOf[ScalaResolveResult]
        sr.element match {
          case x: ScFunction =>
            getterResults.exists {gr =>
              (gr.asInstanceOf[ScalaResolveResult].fromType, sr.fromType) match {
                case (Some(a), Some(b)) => Equivalence.equiv(a, b)
                case _ => false
              }
            }
          case _ => false
        }
    }
  }
}
