package org.jetbrains.plugins.scala.findUsages.factory

import com.intellij.find.findUsages.{FindUsagesHandler, FindUsagesHandlerFactory}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.{PsiElement, PsiNamedElement}
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.findUsages.compilerReferences.search.CompilerIndicesReferencesSearcher._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.light._
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.refactoring.rename.RenameSuperMembersUtil
import org.jetbrains.plugins.scala.util.ImplicitUtil._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.SearchTargetExtractors.ShouldBeSearchedInBytecode
import org.jetbrains.plugins.scala.findUsages.compilerReferences.settings.CompilerIndicesSettings

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.08.2009
 */
class ScalaFindUsagesHandlerFactory(project: Project) extends FindUsagesHandlerFactory { self =>
  private[factory] val typeDefinitionOptions = new ScalaTypeDefinitionFindUsagesOptions(project)
  private[factory] val memberOptions         = new ScalaMemberFindUsagesOptions(project)
  private[factory] val localOptions          = new ScalaLocalFindUsagesOptions(project)

  override def canFindUsages(element: PsiElement): Boolean =
    element match {
      case _: FakePsiMethod          => true
      case _: ScTypedDefinition      => true
      case _: ScTypeDefinition       => true
      case _: ScPrimaryConstructor   => true
      case _: ScTypeParam            => true
      case _: PsiClassWrapper        => true
      case _: PsiMethodWrapper       => true
      case contextBoundElement(_, _) => true
      case _                         => false
    }

  override def createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler = {
    val unwrapped = element match {
      case isWrapper(named)      => named
      case FakePsiMethod(method) => method
      case _                     => element
    }

    val replaced =
      if (!forHighlightUsages) {
        val maybeSuper            = suggestChooseSuper(unwrapped)
        val settings              = CompilerIndicesSettings(project)
        val shouldSearchInBytecode = new ShouldBeSearchedInBytecode(settings)
        maybeSuper.flatMap {
          case shouldSearchInBytecode(target) =>
            val shouldProceed = doBeforeIndicesSearchAction(target)
            shouldProceed.option((target, true))
          case other => (other, false).toOption
        }
      } else Option((unwrapped, false))

    replaced match {
      case Some((e, isImplicit)) =>
        if (isImplicit) new CompilerIndicesFindUsageHandler(e, this)
        else            new ScalaFindUsagesHandler(e, this)
      case None    => FindUsagesHandler.NULL_HANDLER
    }
  }

  private[this] def doBeforeIndicesSearchAction(target: PsiNamedElement): Boolean =
    assertSearchScopeIsSufficient(target).forall(_.runAction())

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
          Messages.showYesNoCancelDialog(e.getProject, message, "Warning", Messages.getQuestionIcon)
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
