package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.ResolveState
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, TypeParameter, TypeSystem, ValType}
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, ScType, ScUndefinedSubstitutor}
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, MethodResolveProcessor, ResolveProcessor}
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveResult}

import scala.collection.{Seq, Set}

/**
  * @author Nikolay.Tropin
  */
case class ExtensionConversionData(baseExpr: ScExpression,
                                   ref: ScExpression,
                                   refName: String,
                                   processor: BaseProcessor,
                                   noApplicability: Boolean,
                                   withoutImplicitsForArgs: Boolean) {

  //TODO! remove this after find a way to improve implicits according to compiler.
  val isHardCoded: Boolean = refName == "+" &&
    baseExpr.getTypeWithoutImplicits().map(_.isInstanceOf[ValType]).getOrElse(false)
  val kinds: Set[ResolveTargets.Value] = processor.kinds

  implicit val typeSystem: TypeSystem = baseExpr.typeSystem

}

object ExtensionConversionHelper {
  def specialExtractParameterType(rr: ScalaResolveResult)(implicit typeSystem: TypeSystem): (Option[ScType], Seq[TypeParameter]) = {
    val typeParams = rr.unresolvedTypeParameters match {
      case Some(tParams) if tParams.nonEmpty => tParams
      case _ => Seq.empty
    }
    val implicitParameterType = InferUtil.extractImplicitParameterType(rr)
    val funType = ScalaPsiManager.instance(rr.element.getProject).cachedFunction1Type
    val result = implicitParameterType match {
      case f@FunctionType(_, _) => Some(f)
      case _ =>
        funType match {
          case Some(ft) =>
            implicitParameterType.conforms(ft, ScUndefinedSubstitutor()) match {
              case (true, undefSubst) =>
                undefSubst.getSubstitutor.map(_.subst(ft).removeUndefines())
              case _ => None
            }
          case _ => None
        }
    }
    (result, typeParams)
  }

  def extensionConversionCheck(data: ExtensionConversionData, candidate: Candidate): Option[Candidate] = {
    ProgressManager.checkCanceled()
    import data._
    val (rr, subst) = candidate

    specialExtractParameterType(rr) match {
      case (Some(FunctionType(tp, _)), _) =>
        if (isHardCoded && tp.isInstanceOf[ValType]) return None

        val hasMethod = checkHasMethodFast(data, tp)

        if (!noApplicability && hasMethod && processor.isInstanceOf[MethodResolveProcessor]) {
          val typeParams = candidate match {
            case (ScalaResolveResult(fun: ScFunction, _), _) if fun.hasTypeParameters => fun.typeParameters.map(TypeParameter(_))
            case _ => Seq.empty
          }
          val fromType = findInType(tp, data, typeParams)

          if (fromType.isEmpty) None
          else if (typeParams.nonEmpty) update(candidate, fromType.get._1)
          else Some(rr, subst)
        }
        else if (hasMethod) Some(rr, subst)
        else None
      case _ => None
    }
  }

  private def update(candidate: Candidate, foundInType: ScalaResolveResult): Option[Candidate] = {
    val (rr, subst) = candidate

    foundInType.resultUndef match {
      case Some(undef) =>
        undef.getSubstitutor match {
          case Some(uSubst) =>
            Some(rr.copy(subst = foundInType.substitutor.followed(uSubst),
              implicitParameterType = rr.implicitParameterType.map(uSubst.subst)),
              subst.followed(uSubst))
          case _ => Some(rr, subst)
        }
      case _ => Some(rr, subst)
    }
  }

  private def findInType(tp: ScType, data: ExtensionConversionData, typeParams: Seq[TypeParameter]): Option[Candidate] = {
    import data._

    def newProcessor(mrp: MethodResolveProcessor) = new MethodResolveProcessor(
      ref, refName, mrp.argumentClauses, mrp.typeArgElements, typeParams, kinds,
      mrp.expectedOption, mrp.isUnderscore, mrp.isShapeResolve, mrp.constructorResolve,
      noImplicitsForArgs = withoutImplicitsForArgs)

    processor match {
      case mrp: MethodResolveProcessor =>
        val newProc = newProcessor(mrp)
        newProc.processType(tp, baseExpr, ResolveState.initial)
        newProc.candidatesS.find(_.isApplicable()).map(x => (x, x.substitutor))
      case _ => None
    }
  }

  private def checkHasMethodFast(data: ExtensionConversionData, tp: ScType) = {
    import data._

    val newProc = new ResolveProcessor(kinds, ref, refName)
    newProc.processType(tp, baseExpr, ResolveState.initial)
    newProc.candidatesS.nonEmpty
  }


}
