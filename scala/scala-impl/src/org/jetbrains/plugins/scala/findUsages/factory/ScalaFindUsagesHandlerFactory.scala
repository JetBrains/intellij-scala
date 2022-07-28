package org.jetbrains.plugins.scala.findUsages.factory

import com.intellij.find.findUsages.{FindUsagesHandler, FindUsagesHandlerFactory}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.{PsiElement, PsiNamedElement}
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.SearchTargetExtractors.{ShouldBeSearchedInBytecode, UsageType}
import org.jetbrains.plugins.scala.findUsages.compilerReferences.search.CompilerIndicesReferencesSearcher._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.settings.CompilerIndicesSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScEnd, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScEndImpl.Target
import org.jetbrains.plugins.scala.lang.psi.light._
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.refactoring.rename.RenameSuperMembersUtil
import org.jetbrains.plugins.scala.util.ImplicitUtil._

class ScalaFindUsagesHandlerFactory(project: Project) extends FindUsagesHandlerFactory { self =>
  private[factory] val typeDefinitionOptions  = new ScalaTypeDefinitionFindUsagesOptions(project)
  private[factory] val memberOptions          = new ScalaMemberFindUsagesOptions(project)
  private[factory] val localOptions           = new ScalaLocalFindUsagesOptions(project)
  private[factory] val compilerIndicesOptions = CompilerIndicesSettings(project)

  override def canFindUsages(element: PsiElement): Boolean =
    element match {
      case _: FakePsiMethod          => true
      case _: ScTypedDefinition      => true
      case _: ScTypeDefinition       => true
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

    val replaced =
      if (!forHighlightUsages) {
        val maybeSuper             = suggestChooseSuper(unwrapped)
        val settings               = CompilerIndicesSettings(project)
        val shouldSearchInBytecode = new ShouldBeSearchedInBytecode(settings)
        maybeSuper.flatMap {
          case shouldSearchInBytecode(target, usageType) =>
            val shouldProceed = doBeforeIndicesSearchAction(target, usageType)
            val isSAMSearch = usageType == UsageType.SAMInterfaceImplementation
            shouldProceed.option((target, !isSAMSearch))
          case other => (other, false).toOption
        }
      } else Option((unwrapped, false))

    replaced match {
      case Some((e, useCompilerIndices)) =>
        if (useCompilerIndices) new CompilerIndicesFindUsagesHandler(e, this)
        else                    new ScalaFindUsagesHandler(e, this)
      case None => FindUsagesHandler.NULL_HANDLER
    }
  }

  private[this] def doBeforeIndicesSearchAction(target: PsiNamedElement, usageType: UsageType): Boolean =
    assertSearchScopeIsSufficient(target, usageType).forall(_.runAction())

  private[this] def suggestChooseSuper(e: PsiElement): Option[PsiNamedElement] = {
    def needToAsk(named: PsiNamedElement): Boolean = named match {
      case fun: ScFunction
          if fun.containingClass.qualifiedName.startsWith(FunctionType.TypeName) && fun.isApplyMethod =>
        false
      case _ => true
    }

    def showDialog(e: ScNamedElement): Option[PsiNamedElement] = {
      import com.intellij.openapi.ui.Messages.{CANCEL, NO, YES}
      val message = ScalaBundle.message("find.usages.member.has.supers", e.name)
      val supers  = RenameSuperMembersUtil.allSuperMembers(e, withSelfType = true).filter(needToAsk)

      val searchForSuperInstead =
        if (supers.nonEmpty)
          Messages.showYesNoCancelDialog(e.getProject, message, ScalaBundle.message("title.warning"), Messages.getQuestionIcon)
        else NO

      searchForSuperInstead match {
        case `YES`    => Option(supers.last)
        case `NO`     => Option(e)
        case `CANCEL` => None
      }
    }

    e match {
      case named: ScNamedElement => showDialog(named)
      case _                     => None
    }
  }
}

object ScalaFindUsagesHandlerFactory {
  def getInstance(project: Project): ScalaFindUsagesHandlerFactory = {
    val extensions = FindUsagesHandlerFactory.EP_NAME.getExtensions(project)
    ContainerUtil.findInstance(extensions, classOf[ScalaFindUsagesHandlerFactory])
  }
}
