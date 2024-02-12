package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.autoImport.GlobalExtensionMethod
import org.jetbrains.plugins.scala.autoImport.GlobalMember.findGlobalMembers
import org.jetbrains.plugins.scala.caches.{ModTracker, cachedInUserData}
import org.jetbrains.plugins.scala.extensions.{NonNullObjectExt, ObjectExt, PsiClassExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector.ImplicitState
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ExtensionIndex
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, FunctionType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{ConstraintSystem, ConstraintsResult, ScType}
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.CommonQualifiedNames.AnyFqn

class ExtensionMethodData(val function: ScFunction,
                          rawExtensionTargetType: ScType,
                          rawReturnType: ScType,
                          val substitutor: ScSubstitutor) {
  private lazy val paramType: ScType =
    substitutor.followed(ScalaPsiUtil.undefineMethodTypeParams(function))(rawExtensionTargetType)

  private lazy val returnType: ScType = substitutor(rawReturnType)

  def withSubstitutor(newSubstitutor: ScSubstitutor): ExtensionMethodData =
    new ExtensionMethodData(function, rawExtensionTargetType, rawReturnType, newSubstitutor)

  override def toString: String = function.name

  def application(fromType: ScType, place: PsiElement): Option[ExtensionMethodApplication] = {
    // to prevent infinite recursion
    if (PsiTreeUtil.isContextAncestor(function.nameContext, place, false))
      return None

    ProgressManager.checkCanceled()

    fromType.conforms(paramType, ConstraintSystem.empty, checkWeak = true) match {
      case ConstraintsResult.Left => None
      case _ =>
        if (function.hasTypeParameters || function.extensionMethodOwner.exists(_.typeParameters.nonEmpty)) {
          implicit val projectContext: ProjectContext = function.projectContext
          implicit val elementScope: ElementScope = function.elementScope

          val functionType = FunctionType(Any, Seq(fromType.tryExtractDesignatorSingleton))
          val implicitState = ImplicitState(
            place = place,
            tp = functionType,
            expandedTp = functionType,
            coreElement = None,
            isImplicitConversion = true,
            searchImplicitsRecursively = 0,
            extensionData = None,
            fullInfo = true,
            previousRecursionState = Some(ImplicitsRecursionGuard.currentMap)
          )
          val resolveResult = new ScalaResolveResult(function, ScSubstitutor.empty)
          val collector = new ImplicitCollector(implicitState)
          val compatible = collector.checkFunctionByType(resolveResult, withLocalTypeInference = true, checkFast = false)

          for {
            srr <- compatible
            resultType <- ExtensionConversionHelper.specialExtractParameterType(srr)
          } yield ExtensionMethodApplication(resultType, srr.implicitParameters)
        } else Some(ExtensionMethodApplication(returnType))
    }
  }
}

object ExtensionMethodData {
  def apply(globalExtensionMethod: GlobalExtensionMethod): Option[ExtensionMethodData] =
    ExtensionMethodData(globalExtensionMethod.function, globalExtensionMethod.substitutor)

  def apply(function: ScFunction, substitutor: ScSubstitutor): Option[ExtensionMethodData] = {
    ProgressManager.checkCanceled()

    val rawCheck: Option[ExtensionMethodData] = cachedInUserData("apply.rawExtensionMethodCheck", function, ModTracker.libraryAware(function), Tuple1(function)) {
      for {
        retType <- function.returnType.toOption
        ext <- function.extensionMethodOwner
        targetTypeElem <- ext.targetTypeElement
        targetType <- targetTypeElem.`type`().toOption
      } yield new ExtensionMethodData(function, targetType, retType, ScSubstitutor.empty)
    }

    rawCheck.map(_.withSubstitutor(substitutor))
  }

  def getPossibleExtensionMethods(expr: ScExpression): Map[GlobalExtensionMethod, ExtensionMethodApplication] =
    if (expr.isInScala3File) {
      expr.getTypeWithoutImplicits().toOption match {
        case None => Map.empty
        case Some(originalType) =>
          val withSuperClasses = originalType.widen.extractClass match {
            case Some(cls) =>
              val classQualifiedName = cls.qualifiedName.pipeIf(cls.is[ScObject])(_ + ".type") // SCL-21153
              MixinNodes.allSuperClasses(cls).map(_.qualifiedName) + classQualifiedName + AnyFqn
            case _ => Set(AnyFqn)
          }
          val scope = expr.resolveScope
          implicit val project: Project = expr.getProject
          (for {
            qName <- withSuperClasses
            extensionMethodCandidate <- ExtensionIndex.extensionMethodCandidatesForFqn(qName, scope)

            if ImplicitProcessor.isAccessible(extensionMethodCandidate, expr)

            method <- findGlobalMembers(extensionMethodCandidate, scope)(GlobalExtensionMethod)
            data <- ExtensionMethodData(method)
            application <- data.application(originalType, expr)
          } yield method -> application)
            .toMap
      }
    } else Map.empty
}
