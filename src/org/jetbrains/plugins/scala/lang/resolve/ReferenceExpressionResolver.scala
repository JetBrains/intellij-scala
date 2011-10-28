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
import collection.Set
import psi.types.result.TypingContext
import psi.types.nonvalue.{TypeParameter, ScTypePolymorphicType}

class ReferenceExpressionResolver(shapesOnly: Boolean)
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
      case call: ScMethodCall if !call.isUpdateCall =>
        ContextInfo(Some(call.argumentExpressions), () => None, false)
      case call: ScMethodCall =>
        val args = call.argumentExpressions ++ call.getContext.asInstanceOf[ScAssignStmt].getRExpression.toList
        ContextInfo(Some(args), () => None, false)
      case section: ScUnderscoreSection => ContextInfo(None, () => section.expectedType(), true)
      case inf: ScInfixExpr if ref == inf.operation => {
        ContextInfo(if (ref.rightAssoc) Some(Seq(inf.lOp)) else inf.rOp match {
          case tuple: ScTuple => Some(tuple.exprs) // See SCL-2001
          case unit: ScUnitExpr => Some(Nil) // See SCL-3485
          case rOp => Some(Seq(rOp))
        }, () => None, false)
      }
      case parents: ScParenthesisedExpr => getContextInfo(ref, parents)
      case postf: ScPostfixExpr if ref == postf.operation => getContextInfo(ref, postf)
      case pref: ScPrefixExpr if ref == pref.operation => getContextInfo(ref, pref)
      case _ => ContextInfo(None, () => e.expectedType(), false)
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

    val info = getContextInfo(reference, reference)

    //expectedOption different for cases
    // val a: (Int) => Int = foo
    // and for case
    // val a: (Int) => Int = _.foo
    val expectedOption = () => {
      val expr: PsiElement = reference.getContext match {
        case parent@(_: ScPrefixExpr | _: ScPostfixExpr | _: ScInfixExpr) => parent
        case _ => reference
      }
      val unders = ScUnderScoreSectionUtil.underscores(expr)
      if (unders.length != 0) {
        info.expectedType.apply() match {
          case Some(ScFunctionType(ret, _)) => Some(ret)
          case Some(p: ScParameterizedType) if p.getFunctionType != None => Some(p.typeArgs.last)
          case other => other
        }
      } else info.expectedType.apply()
    }

    val prevInfoTypeParams = reference.qualifier match {
      case Some(s: ScSuperReference) => Seq.empty
      case Some(qual) =>
        qual.getNonValueType(TypingContext.empty).map {
          case t: ScTypePolymorphicType => t.typeParameters
          case _ => Seq.empty
        }.getOrElse(Seq.empty)
      case _ => reference.getContext match {
        case sugar: ScSugarCallExpr if sugar.operation == reference =>
          sugar.getBaseExpr.getNonValueType(TypingContext.empty).map {
            case t: ScTypePolymorphicType => t.typeParameters
            case _ => Seq.empty
          }.getOrElse(Seq.empty)
        case _ => Seq.empty
      }
    }

    def nonAssignResolve: Array[ResolveResult] = {
      def processor(smartProcessor: Boolean): MethodResolveProcessor =
        new MethodResolveProcessor(reference, name, info.arguments.toList,
          getTypeArgs(reference), prevInfoTypeParams, kinds(reference, reference, incomplete), expectedOption,
          info.isUnderscore, shapesOnly, enableTupling = true) {
          override def candidatesS: Set[ScalaResolveResult] = {
            if (!smartProcessor) super.candidatesS
            else {
              levelSet ++= reference.shapeResolve.map(_.asInstanceOf[ScalaResolveResult])
              super.candidatesS
            }
          }
        }

      var result: Array[ResolveResult] = Array.empty
      if (shapesOnly) {
        result = reference.doResolve(reference, processor(false))
      } else {
        val candidatesS = processor(true).candidatesS //let's try to avoid treeWalkUp
        if (candidatesS.isEmpty || candidatesS.forall(!_.isApplicable)) {
          // it has another resolve only in one case:
          // clazz.ref(expr)
          // clazz has method ref with one argument, but it's not ok
          // so shape resolve return this wrong result
          // however there is implicit conversion with right argument
          // this is ugly, but it can improve performance
          result = reference.doResolve(reference, processor(false))
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

    reference.getContext match {
      case assign: ScAssignStmt if assign.getLExpression == reference
              && !reference.getContext.getContext.isInstanceOf[ScArgumentExprList] =>
        // SLS 6.1.5 "The interpretation of an assignment to a simple variable x = e depends on the definition of x."

        // If x denotes a mutable variable, then the assignment changes the current value of x to be
        // the result of evaluating the expression e
        val processor = new MethodResolveProcessor(reference, name, info.arguments.toList,
          getTypeArgs(reference), prevInfoTypeParams, /*todo refExprLastRef? */StdKinds.varsRef, () => None,
          info.isUnderscore, shapesOnly)
        val result = reference.doResolve(reference, processor)

        /*
        todo: this is wrong algorithm
        resolve should work in one pass (not three)
        after that if resolve found something we should check if it has appropriate setter
        and change resolve result to this setter
        @see SCL-3191
         */
        if (result.nonEmpty) result
        else {
          val setterResult = searchForSetter(reference, assign, info.isUnderscore, prevInfoTypeParams)
          if (setterResult.nonEmpty) setterResult
          else nonAssignResolve // resolve to val, to be able to highlight 'reassignment to val.
        }
      case _ => nonAssignResolve
    }
  }

  // See SCL-2868
  // "If x is a parameterless function defined in some template, and the same template contains a setter function x_= as member,
  // then the assignment x = e is interpreted as the invocation x_=(e ) of that setter function.
  // Analogously, an assignment f.x = e to a parameterless function x is interpreted as the invocation f.x_=(e).
  def searchForSetter(ref: ResolvableReferenceExpression, assign: ScAssignStmt, isUnderscore: Boolean,
                      infoTypeParams: Seq[TypeParameter]): Array[ResolveResult] = {
    val getterResults = ref.doResolve(ref, new MethodResolveProcessor(ref, ref.refName, Nil, Nil, infoTypeParams,
      kinds = StdKinds.methodsOnly))

    val args = List(assign.getRExpression.toList.map(Expression(_)))
    val setterName = ref.refName + "_="
    val setterResults = ref.doResolve(ref, new MethodResolveProcessor(ref, setterName, args, Nil, infoTypeParams,
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
