package org.jetbrains.plugins.scala
package lang
package resolve

import _root_.com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.{PsiElement, ResolveResult}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.collection.Set

class ReferenceExpressionResolver(shapesOnly: Boolean)
        extends ResolveCache.PolyVariantResolver[ResolvableReferenceExpression] {
  case class ContextInfo(arguments: Option[Seq[Expression]], expectedType: () => Option[ScType], isUnderscore: Boolean)
  
  private def argumentsOf(ref: PsiElement): Seq[Expression] = {
    ref.getContext match {
      case infixExpr: ScInfixExpr =>
        //TODO should rOp really be parsed as Tuple (not as argument list)?
        infixExpr.rOp match {
          case t: ScTuple => t.exprs
          case op => Seq(op)
        }
      case methodCall: ScMethodCall => methodCall.argumentExpressions
    }
  }

  private def getContextInfo(ref: ResolvableReferenceExpression, e: ScExpression): ContextInfo = {
    e.getContext match {
      case generic : ScGenericCall => getContextInfo(ref, generic)
      case call: ScMethodCall if !call.isUpdateCall =>
        ContextInfo(Some(call.argumentExpressions), () => call.expectedType(), isUnderscore = false)
      case call: ScMethodCall =>
        val args = call.argumentExpressions ++ call.getContext.asInstanceOf[ScAssignStmt].getRExpression.toList
        ContextInfo(Some(args), () => None, isUnderscore = false)
      case section: ScUnderscoreSection => ContextInfo(None, () => section.expectedType(), isUnderscore = true)
      case inf: ScInfixExpr if ref == inf.operation =>
        ContextInfo(if (ref.rightAssoc) Some(Seq(inf.lOp)) else inf.rOp match {
          case tuple: ScTuple => Some(tuple.exprs) // See SCL-2001
          case unit: ScUnitExpr => Some(Nil) // See SCL-3485
          case e: ScParenthesisedExpr => e.expr match {
            case Some(expr) => Some(Seq(expr))
            case _ => Some(Nil)
          }
          case rOp => Some(Seq(rOp))
        }, () => None, isUnderscore = false)
      case parents: ScParenthesisedExpr => getContextInfo(ref, parents)
      case postf: ScPostfixExpr if ref == postf.operation => getContextInfo(ref, postf)
      case pref: ScPrefixExpr if ref == pref.operation => getContextInfo(ref, pref)
      case _ => ContextInfo(None, () => e.expectedType(), isUnderscore = false)
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
      case _ => ref.getKinds(incomplete)
    }
  }

  private def getTypeArgs(e : ScExpression) : Seq[ScTypeElement] = {
    e.getContext match {
      case generic: ScGenericCall => generic.arguments
      case parents: ScParenthesisedExpr => getTypeArgs(parents)
      case _ => Seq.empty
    }
  }

  def resolve(reference: ResolvableReferenceExpression, incomplete: Boolean): Array[ResolveResult] = {
    val name = if(reference.isUnaryOperator) "unary_" + reference.refName else reference.refName

    if (name == ScImplicitlyConvertible.IMPLICIT_REFERENCE_NAME) {
      val data = reference.getUserData(ScImplicitlyConvertible.FAKE_RESOLVE_RESULT_KEY)
      if (data != null) return Array(data)
    }

    val info = getContextInfo(reference, reference)

    //expectedOption different for cases
    // val a: (Int) => Int = foo
    // and for case
    // val a: (Int) => Int = _.foo
    val expectedOption = () => info.expectedType.apply()

    val prevInfoTypeParams = reference.getPrevTypeInfoParams

    implicit val typeSystem = reference.getProject.typeSystem
    def nonAssignResolve: Array[ResolveResult] = {
      def processor(smartProcessor: Boolean): MethodResolveProcessor =
        new MethodResolveProcessor(reference, name, info.arguments.toList,
          getTypeArgs(reference), prevInfoTypeParams, kinds(reference, reference, incomplete), expectedOption,
          info.isUnderscore, shapesOnly, enableTupling = true) {
          override def candidatesS: Set[ScalaResolveResult] = {
            if (!smartProcessor) super.candidatesS
            else {
              val iterator = reference.shapeResolve.map(_.asInstanceOf[ScalaResolveResult]).iterator
              while (iterator.hasNext) {
                levelSet.add(iterator.next())
              }
              super.candidatesS
            }
          }
        }

      var result: Array[ResolveResult] = Array.empty
      if (shapesOnly) {
        result = reference.doResolve(reference, processor(smartProcessor = false))
      } else {
        val candidatesS = processor(smartProcessor = true).candidatesS //let's try to avoid treeWalkUp
        if (candidatesS.isEmpty || candidatesS.forall(!_.isApplicable())) {
          // it has another resolve only in one case:
          // clazz.ref(expr)
          // clazz has method ref with one argument, but it's not ok
          // so shape resolve return this wrong result
          // however there is implicit conversion with right argument
          // this is ugly, but it can improve performance
          result = reference.doResolve(reference, processor(smartProcessor = false))
        } else {
          result = candidatesS.toArray
        }
      }
      if (result.isEmpty && reference.isAssignmentOperator) {
        val assignProcessor = new MethodResolveProcessor(reference, reference.refName.init, List(argumentsOf(reference)),
          Nil, prevInfoTypeParams, isShapeResolve = shapesOnly, enableTupling = true)
        result = reference.doResolve(reference, assignProcessor)
        result.map(r => r.asInstanceOf[ScalaResolveResult].copy(isAssignment = true): ResolveResult)
      } else {
        result
      }
    }

    nonAssignResolve
  }
}
