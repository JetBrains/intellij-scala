package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.ResolveState
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, TypeParameter, TypeSystem, ValType}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScUndefinedSubstitutor}
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
    baseExpr.getTypeWithoutImplicits().exists {
      _.isInstanceOf[ValType]
    }
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

    specialExtractParameterType(candidate._1) match {
      case (Some(FunctionType(tp, _)), _) =>
        if (isHardCoded && tp.isInstanceOf[ValType]) return None

        val hasMethod = checkHasMethodFast(data, tp)

        if (!noApplicability && hasMethod && processor.isInstanceOf[MethodResolveProcessor]) {
          val typeParams = candidate match {
            case (ScalaResolveResult(fun: ScFunction, _), _) if fun.hasTypeParameters => fun.typeParameters.map(TypeParameter(_))
            case _ => Seq.empty
          }

          findInType(tp, data, typeParams).map { tp =>
            typeParams match {
              case Seq() => candidate
              case _ => update(candidate, tp)
            }
          }
        }
        else if (hasMethod) Some(candidate)
        else None
      case _ => None
    }
  }

  private def update(candidate: Candidate, foundInType: ScalaResolveResult): Candidate = {
    val (candidateResult, candidateSubstitutor) = candidate

    foundInType.resultUndef.flatMap {
      _.getSubstitutor
    }.map { substitutor =>
      val result = candidateResult.copy(subst = foundInType.substitutor.followed(substitutor),
        implicitParameterType = candidateResult.implicitParameterType.map(substitutor.subst))

      (result, candidateSubstitutor.followed(substitutor))
    }.getOrElse {
      candidate
    }
  }

  private def findInType(tp: ScType, data: ExtensionConversionData, typeParams: Seq[TypeParameter]): Option[ScalaResolveResult] = {
    import data._

    Option(processor).collect {
      case processor: MethodResolveProcessor => processor
    }.map { processor =>
      new MethodResolveProcessor(
        ref, refName, processor.argumentClauses, processor.typeArgElements, typeParams, kinds,
        processor.expectedOption, processor.isUnderscore, processor.isShapeResolve, processor.constructorResolve,
        noImplicitsForArgs = withoutImplicitsForArgs)
    }.flatMap { processor =>
      processor.processType(tp, baseExpr, ResolveState.initial)
      processor.candidatesS.find(_.isApplicable())
    }
  }

  private def checkHasMethodFast(data: ExtensionConversionData, tp: ScType) = {
    import data._

    val newProc = new ResolveProcessor(kinds, ref, refName)
    newProc.processType(tp, baseExpr, ResolveState.initial)
    newProc.candidatesS.nonEmpty
  }
}
