package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.autoImport.GlobalImplicitConversion
import org.jetbrains.plugins.scala.autoImport.GlobalMember.findGlobalMembers
import org.jetbrains.plugins.scala.caches.{ModTracker, cachedInUserData}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiClassExt, PsiElementExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector.ImplicitState
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ImplicitConversionIndex
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, FunctionType, StdTypes}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.types.{ConstraintSystem, ConstraintsResult, ScParameterizedType, ScType}
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.CommonQualifiedNames.AnyFqn

import scala.annotation.tailrec

abstract class ImplicitConversionData {
  def element: PsiNamedElement

  protected def paramType: ScType
  protected def returnType: ScType
  protected def substitutor: ScSubstitutor

  def withSubstitutor(substitutor: ScSubstitutor): ImplicitConversionData

  override def toString: String = element.name

  def isApplicable(fromType: ScType, place: PsiElement): Option[ImplicitConversionApplication] = {
    // to prevent infinite recursion
    if (PsiTreeUtil.isContextAncestor(element.nameContext, place, false))
      return None

    ProgressManager.checkCanceled()

    fromType.conforms(paramType, ConstraintSystem.empty, checkWeak = true) match {
      case ConstraintsResult.Left => None
      case _ =>
        element match {
          case f: ScFunction if f.hasTypeParameters =>
            returnTypeWithLocalTypeInference(f, fromType, place)
          case _ =>
            Some(ImplicitConversionApplication(returnType))
        }
    }
  }

  private def returnTypeWithLocalTypeInference(function: ScFunction,
                                               fromType: ScType,
                                               place: PsiElement): Option[ImplicitConversionApplication] = {

    implicit val projectContext: ProjectContext = function.projectContext
    implicit val elementScope: ElementScope = function.elementScope

    val functionType = FunctionType(Any, Seq(fromType.tryExtractDesignatorSingleton))
    val implicitState = ImplicitState(place, functionType, functionType, None, isImplicitConversion = true,
      searchImplicitsRecursively = 0, None, fullInfo = true, Some(ImplicitsRecursionGuard.currentMap))
    val resolveResult = new ScalaResolveResult(function, ScSubstitutor.empty)
    val collector = new ImplicitCollector(implicitState)
    val compatible = collector.checkFunctionByType(resolveResult, withLocalTypeInference = true, checkFast = false)

    for {
      srr            <- compatible
      conversionType <- srr.implicitParameterType
      resultType     <- resultType(conversionType)
    } yield {
      ImplicitConversionApplication(resultType, srr.implicitParameters)
    }
  }

  @tailrec
  private def resultType(conversionType: ScType, isResult: Boolean = false): Option[ScType] = conversionType match {
    case FunctionType(res, _) => resultType(res, isResult = true)
    case _ if isResult        => Option(conversionType)
    case _                    => None
  }
}

object ImplicitConversionData {

  def apply(globalConversion: GlobalImplicitConversion): Option[ImplicitConversionData] =
    ImplicitConversionData(globalConversion.function, globalConversion.substitutor)

  def apply(element: PsiNamedElement, substitutor: ScSubstitutor): Option[ImplicitConversionData] = {
    ProgressManager.checkCanceled()

    element match {
      case function: ScFunction if function.isImplicitConversion => fromRegularImplicitConversion(function, substitutor)
      case function: ScFunction if !function.isParameterless     => None
      case typeable: Typeable                                    => fromElementWithFunctionType(typeable, substitutor)
      case _                                                     => None
    }
  }

  def getPossibleConversions(expr: ScExpression): Map[GlobalImplicitConversion, ImplicitConversionApplication] =
    expr.getTypeWithoutImplicits().toOption match {
      case None => Map.empty
      case Some(originalType) =>
        val withSuperClasses = originalType.widen.extractClass match {
          case Some(clazz) =>
            val classQualifiedName = clazz.qualifiedName.pipeIf(clazz.is[ScObject])(_ + ".type") // SCL-21153
            MixinNodes.allSuperClasses(clazz).map(_.qualifiedName) + classQualifiedName + AnyFqn
          case _ => Set(AnyFqn)
        }
        val scope = expr.resolveScope
        (for {
          qName    <- withSuperClasses
          function <- ImplicitConversionIndex.conversionCandidatesForFqn(qName, scope)(expr.getProject)

          if ImplicitConversionProcessor.applicable(function, expr)

          conversion  <- findGlobalMembers(function, scope)(GlobalImplicitConversion)
          data        <- ImplicitConversionData(conversion)
          application <- data.isApplicable(originalType, expr)
        } yield (conversion, application))
          .toMap
    }

  private def fromRegularImplicitConversion(function: ScFunction, substitutor: ScSubstitutor): Option[ImplicitConversionData] = {
    val rawCheck: Option[ImplicitConversionData] = cachedInUserData("fromRegularImplicitConversion.rawCheck", function, ModTracker.libraryAware(function), Tuple1(function)) {
      for {
        retType   <- function.returnType.toOption
        param <- function.parameters.headOption
        paramType <- param.`type`().toOption
      } yield {
        new RegularImplicitConversionData(function, paramType, retType, ScSubstitutor.empty)
      }
    }

    rawCheck.map(_.withSubstitutor(substitutor))
  }

  private def fromElementWithFunctionType(named: PsiNamedElement with Typeable, substitutor: ScSubstitutor): Option[ImplicitConversionData] = {
    val rawCheck: Option[ImplicitConversionData] = cachedInUserData("fromElementWithFunctionType.rawCheck", named, ModTracker.libraryAware(named), Tuple1(named)) {
      for {
        function1Type <- named.elementScope.cachedFunction1Type
        elementType   <- named.`type`().toOption
        if elementType.conforms(function1Type)
      } yield {
        new ElementWithFunctionTypeData(named, elementType, ScSubstitutor.empty)
      }
    }

    rawCheck.map(_.withSubstitutor(substitutor))
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

    override def withSubstitutor(substitutor: ScSubstitutor): ImplicitConversionData =
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

    override def withSubstitutor(substitutor: ScSubstitutor): ImplicitConversionData =
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