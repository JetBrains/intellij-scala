package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.findImplicits
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.types.api.StdTypes
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, Stop}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypeResult, Typeable}
import org.jetbrains.plugins.scala.lang.psi.types.{ConstraintSystem, ConstraintsResult, ScParameterizedType, ScType}
import org.jetbrains.plugins.scala.macroAnnotations.{CachedInUserData, ModCount}
import org.jetbrains.plugins.scala.project.ProjectContext

abstract class ImplicitConversionData {
  def element: PsiNamedElement

  protected def paramType: ScType
  protected def returnType: ScType
  protected def substitutor: ScSubstitutor

  def withSubstitutor(s: ScSubstitutor): ImplicitConversionData

  override def toString: String = element.name

  def isCompatible(fromType: ScType, place: PsiElement): Either[String, (ScType, ScSubstitutor)] = {
    // to prevent infinite recursion
    if (PsiTreeUtil.isContextAncestor(element.nameContext, place, false))
      return Left(ScalaBundle.message("conversion.is.not.available.in.it.s.own.definition"))

    ProgressManager.checkCanceled()

    fromType.conforms(paramType, ConstraintSystem.empty, checkWeak = true) match {
      case ConstraintsResult.Left => conformanceFailure(fromType, paramType)
      case system: ConstraintSystem =>
        element match {
          case f: ScFunction if f.hasTypeParameters =>
            returnTypeWithLocalTypeInference(f, fromType, place, system)
          case _ =>
            Right((returnType, ScSubstitutor.empty))
        }
    }
  }

  def resultType(from: ScType, place: PsiElement): Option[ScType] =
    isCompatible(from: ScType, place: PsiElement).toOption.map(_._1)

  private def returnTypeWithLocalTypeInference(function: ScFunction,
                                               fromType: ScType,
                                               place: PsiElement,
                                               constraints: ConstraintSystem): Either[String, (ScType, ScSubstitutor)] = {

    implicit val projectContext: ProjectContext = function.projectContext

    constraints match {
      case ConstraintSystem(unSubst) =>
        val typeParameters = function.typeParameters.map { typeParameter =>
          typeParameter -> typeParameter.typeParamId
        }
        val typeParamIds = typeParameters.map(_._2).toSet

        var lastConstraints = constraints
        val boundsSubstitutor = substitutor.andThen(unSubst)

        def substitute(maybeBound: TypeResult) =
          for {
            bound <- maybeBound.toOption
            substituted = boundsSubstitutor(bound)
            if !substituted.hasRecursiveTypeParameters(typeParamIds)
          } yield substituted

        for {
          (typeParameter, typeParamId) <- typeParameters
        } {
          lastConstraints = substitute(typeParameter.lowerBound).fold(lastConstraints) {
            lastConstraints.withLower(typeParamId, _)
          }

          lastConstraints = substitute(typeParameter.upperBound).fold(lastConstraints) {
            lastConstraints.withUpper(typeParamId, _)
          }
        }

        lastConstraints match {
          case ConstraintSystem(lastSubstitutor) =>
            val clauses = function.paramClauses.clauses

            val parameters = clauses.headOption.toSeq.flatMap(_.parameters).map(Parameter(_))

            val dependentSubstitutor = ScSubstitutor.paramToType(parameters, Seq.fill(parameters.length)(fromType))

            def dependentMethodTypes: Option[ScParameterClause] =
              function.returnType.toOption.flatMap { functionType =>
                clauses match {
                  case Seq(_, last) if last.isImplicit =>
                    var result: Option[ScParameterClause] = None
                    functionType.recursiveUpdate { t =>
                      t match {
                        case ScDesignatorType(p: ScParameter) if last.parameters.contains(p) =>
                          result = Some(last)
                        case _ =>
                      }
                      if (result.isDefined) Stop
                      else ProcessSubtypes
                    }

                    result
                  case _ => None
                }
              }

            val effectiveParameters = dependentMethodTypes.toSeq
              .flatMap(_.effectiveParameters)
              .map(Parameter(_))

            val (inferredParameters, expressions, _) = findImplicits(effectiveParameters, None, place, canThrowSCE = false,
              abstractSubstitutor = substitutor.followed(dependentSubstitutor).followed(unSubst))

            Right(
              lastSubstitutor(dependentSubstitutor(returnType)),
              ScSubstitutor.paramToExprType(inferredParameters, expressions, useExpected = false)
            )
          case _ => problematicBounds(fromType)
        }
      case _ => problematicBounds(fromType)
    }
  }

  private def problematicBounds(fromType: ScType) =
    Left(ScalaBundle.message("element.has.incompatible.type.parameter.bounds.for.type", element.name, fromType))

  private def conformanceFailure(fromType: ScType, paramType: ScType) =
    Left(ScalaBundle.message("type.does.not.conform.to.type", fromType, paramType))
}

object ImplicitConversionData {

  def apply(element: PsiNamedElement, substitutor: ScSubstitutor): Option[ImplicitConversionData] = {
    ProgressManager.checkCanceled()

    element match {
      case function: ScFunction if function.isImplicitConversion => fromRegularImplicitConversion(function, substitutor)
      case function: ScFunction if !function.isParameterless     => None
      case typeable: Typeable                                    => fromElementWithFunctionType(typeable, substitutor)
      case _                                                     => None
    }
  }

  @CachedInUserData(function, ModCount.getBlockModificationCount)
  private def rawCheck(function: ScFunction): Option[ImplicitConversionData] = {
    for {
      retType   <- function.returnType.toOption
      param <- function.parameters.headOption
      paramType <- param.`type`().toOption
    } yield {
      new RegularImplicitConversionData(function, paramType, retType, ScSubstitutor.empty)
    }
  }

  @CachedInUserData(named, ModCount.getBlockModificationCount)
  private def rawElementWithFunctionTypeCheck(named: PsiNamedElement with Typeable): Option[ImplicitConversionData] = {
    for {
      function1Type <- named.elementScope.cachedFunction1Type
      elementType   <- named.`type`().toOption
      if elementType.conforms(function1Type)
    } yield {
      new ElementWithFunctionTypeData(named, elementType, ScSubstitutor.empty)
    }
  }

  private def fromRegularImplicitConversion(function: ScFunction,
                                            substitutor: ScSubstitutor): Option[ImplicitConversionData] = {
    rawCheck(function).map(_.withSubstitutor(substitutor))
  }

  private def fromElementWithFunctionType(named: PsiNamedElement with Typeable,
                                          substitutor: ScSubstitutor): Option[ImplicitConversionData] = {
    rawElementWithFunctionTypeCheck(named).map(_.withSubstitutor(substitutor))
  }


  private class RegularImplicitConversionData(override val element: PsiNamedElement,
                                              rawParamType: ScType,
                                              rawReturnType: ScType,
                                              override val substitutor: ScSubstitutor) extends ImplicitConversionData {

    protected override lazy val paramType: ScType = {
      val undefiningSubst = element match {
        case fun: ScFunction => ScalaPsiUtil.undefineMethodTypeParams(fun)
        case _               => ScSubstitutor.empty
      }
      substitutor.followed(undefiningSubst)(rawParamType)
    }

    protected override lazy val returnType: ScType = substitutor(rawReturnType)

    override def withSubstitutor(s: ScSubstitutor): ImplicitConversionData =
      new RegularImplicitConversionData(element, rawParamType, rawReturnType, substitutor)
  }

  private class ElementWithFunctionTypeData(override val element: PsiNamedElement with Typeable,
                                            rawElementType: ScType,
                                            override val substitutor: ScSubstitutor = ScSubstitutor.empty)
    extends ImplicitConversionData {
    private def stdTypes = StdTypes.instance(element.getProject)

    private lazy val functionTypeParams: Option[(ScType, ScType)] = {
      val undefiningSubst = element match {
        case fun: ScFunction => ScalaPsiUtil.undefineMethodTypeParams(fun)
        case _               => ScSubstitutor.empty
      }
      for {
        functionType <- element.elementScope.cachedFunction1Type
        elementType <- element.`type`().toOption.map(substitutor.followed(undefiningSubst))
        (paramType, retType) <- extractFunctionTypeParameters(elementType, functionType)
      } yield (paramType, retType)
    }

    override protected def paramType: ScType = functionTypeParams.map(_._1).getOrElse(stdTypes.Nothing)

    override protected def returnType: ScType = functionTypeParams.map(_._2).getOrElse(stdTypes.Any)

    override def withSubstitutor(s: ScSubstitutor): ImplicitConversionData =
      new ElementWithFunctionTypeData(element, rawElementType, substitutor)

    private def extractFunctionTypeParameters(functionTypeCandidate: ScType,
                                              functionType: ScParameterizedType): Option[(ScType, ScType)] = {
      implicit val projectContext: ProjectContext = functionType.projectContext

      functionTypeCandidate.conforms(functionType, ConstraintSystem.empty) match {
        case ConstraintSystem(newSubstitutor) =>
          functionType.typeArguments.map(newSubstitutor) match {
            case Seq(argType, retType) => Some((argType, retType))
            case _                     => None
          }
        case _ => None
      }
    }
  }


}