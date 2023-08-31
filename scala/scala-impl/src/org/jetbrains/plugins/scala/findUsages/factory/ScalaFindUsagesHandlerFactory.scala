package org.jetbrains.plugins.scala.findUsages.factory

import com.intellij.find.findUsages.{FindUsagesHandler, FindUsagesHandlerFactory}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.findUsages.SearchTargetExtractors.ShouldBeSearchedInBytecode
import org.jetbrains.plugins.scala.findUsages.{ExternalSearchScopeChecker, UsageType}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScEnd, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScEndImpl.Target
import org.jetbrains.plugins.scala.lang.psi.light._
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.refactoring.rename.RenameSuperMembersUtil
import org.jetbrains.plugins.scala.settings.CompilerIndicesSettings
import org.jetbrains.plugins.scala.util.ImplicitUtil._

class ScalaFindUsagesHandlerFactory(project: Project) extends FindUsagesHandlerFactory { self =>

  override def canFindUsages(element: PsiElement): Boolean =
    element match {
      case _: FakePsiMethod          => true
      case _: ScTypedDefinition      => true
      case _: ScTypeDefinition       => true
      case _: ScTypeAlias            => true
      case _: ScPrimaryConstructor   => true
      case _: ScTypeParam            => true
      case _: PsiClassWrapper        => true
      case _: PsiMethodWrapper[_]    => true
      case contextBoundElement(_, _) => true
      case _                         => false
    }

  override def createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler = {
    val unwrapped = element match {
      case isWrapper(named)      => named
      case FakePsiMethod(method) => method
      case Target(ScEnd(Some(begin), _)) => if (begin.tag.is[ScNamedElement]) begin.tag else begin
      case _                     => element
    }

    val config = ScalaFindUsagesConfiguration.getInstance(project)

    val replaced =
      if (!forHighlightUsages) {
        val maybeSuper = maybeChooseSuper(unwrapped, config)
        val settings = CompilerIndicesSettings(project)
        val shouldSearchInBytecode = new ShouldBeSearchedInBytecode(settings)
        maybeSuper.flatMap {
          case shouldSearchInBytecode(target, usageType) =>
            val shouldProceed = doBeforeIndicesSearchAction(target, usageType)
            val isSAMSearch = usageType == UsageType.SAMInterfaceImplementation
            shouldProceed.option((target, !isSAMSearch))
          case other =>
            Some(other, false)
        }
      }
      else Some((unwrapped, false))

    replaced match {
      case Some((e, useCompilerIndices)) =>
        if (useCompilerIndices) new CompilerIndicesFindUsagesHandler(e, config)
        else                    new ScalaFindUsagesHandler(e, config)
      case None =>
        FindUsagesHandler.NULL_HANDLER
    }
  }

  private[this] def doBeforeIndicesSearchAction(target: PsiNamedElement, usageType: UsageType): Boolean =
    ExternalSearchScopeChecker.checkSearchScopeIsSufficientExternally(target, usageType)

  private def maybeChooseSuper(element: PsiElement, config: ScalaFindUsagesConfiguration) = element match {
    case pc: ScPrimaryConstructor => Some(pc)
    case named: ScNamedElement =>
      if (config.getMemberOptions.isSearchForBaseMember)
        Some(getDeepestSuperElement(named))
      else
        Some(named)
    case _ =>
      None
  }

  private def getDeepestSuperElement(named: ScNamedElement): PsiNamedElement = {
    val supersAll = RenameSuperMembersUtil.allSuperMembers(named, withSelfType = true)
    val supers = supersAll.filter(isGoodSuperCandidate)
    supers.lastOption.getOrElse(named)
  }

  private def isGoodSuperCandidate(named: PsiNamedElement): Boolean = named match {
    case fun: ScFunction =>
      val isFunctionApplyMethod = fun.containingClass.qualifiedName.startsWith(FunctionType.TypeName) && fun.isApplyMethod
      !isFunctionApplyMethod
    case _ => true
  }
}