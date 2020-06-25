package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, TypeParameter, ValType}
import org.jetbrains.plugins.scala.lang.psi.types.{ConstraintSystem, ScType}
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, MethodResolveProcessor, ResolveProcessor}
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveResult, ScalaResolveState}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.{Seq, Set}

/**
  * @author Nikolay.Tropin
  */
case class ExtensionConversionData(place: ScExpression,
                                   ref: ScExpression,
                                   refName: String,
                                   processor: BaseProcessor,
                                   noApplicability: Boolean,
                                   withoutImplicitsForArgs: Boolean) {

  //TODO! remove this after find a way to improve implicits according to compiler.
  val isHardCoded: Boolean = refName == "+" &&
    place.getTypeWithoutImplicits().exists {
      _.isInstanceOf[ValType]
    }
  val kinds: Set[ResolveTargets.Value] = processor.kinds
}

object ExtensionConversionHelper {

  def specialExtractParameterType(resolveResult: ScalaResolveResult): Option[ScType] =
    InferUtil.extractImplicitParameterType(resolveResult).flatMap {
      case FunctionType(resultType, _) => Some(resultType)
      case implicitParameterType =>
        implicit val project: Project = resolveResult.element.getProject
        for {
          functionType <- ElementScope(project).cachedFunction1Type
          substituted <- implicitParameterType.conforms(functionType, ConstraintSystem.empty) match {
            case ConstraintSystem(substitutor) => Some(substitutor(functionType))
            case _ => None
          }

          (resultType, _) <- FunctionType.unapply(substituted.removeAbstracts)
        } yield resultType
    }

  def extensionConversionCheck(data: ExtensionConversionData, candidate: ScalaResolveResult): Option[ScalaResolveResult] = {
    ProgressManager.checkCanceled()
    import data._

    specialExtractParameterType(candidate).filter {
      case _: ValType if isHardCoded => false
      case _                         => true
    }.filter {
      checkHasMethodFast(data, _)
    }.flatMap { tp =>
      if (!noApplicability && processor.isInstanceOf[MethodResolveProcessor]) {
        val typeParams = candidate match {
          case ScalaResolveResult(function: ScFunction, _) if function.hasTypeParameters =>
            function.typeParameters.map {
              TypeParameter(_)
            }
          case _ => Seq.empty
        }

        findInType(tp, data, typeParams).map { tp =>
          typeParams match {
            case Seq() => candidate
            case _     => update(candidate, tp)
          }
        }
      }
      else Some(candidate)
    }
  }

  private def update(candidate: ScalaResolveResult, foundInType: ScalaResolveResult)
                    (implicit context: ProjectContext = foundInType.projectContext): ScalaResolveResult = {

    foundInType.resultUndef match {
      case None => candidate
      case Some(ConstraintSystem(substitutor)) =>
        val parameterType = candidate.implicitParameterType

        val combinedSubstitutor = candidate.substitutor.followed(foundInType.substitutor).followed(substitutor)
        candidate.copy(
          subst = combinedSubstitutor,
          implicitParameterType = parameterType.map(combinedSubstitutor)
        )
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
      processor.processType(tp, place, ScalaResolveState.empty)
      processor.candidatesS.find(_.isApplicable())
    }
  }

  private def checkHasMethodFast(data: ExtensionConversionData, tp: ScType) = {
    import data._

    val newProc = new ResolveProcessor(kinds, ref, refName)
    newProc.processType(tp, place, ScalaResolveState.empty)
    newProc.candidatesS.nonEmpty
  }
}
