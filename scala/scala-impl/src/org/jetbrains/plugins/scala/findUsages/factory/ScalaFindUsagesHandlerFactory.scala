package org.jetbrains.plugins.scala.findUsages.factory

import com.intellij.find.FindManager
import com.intellij.find.findUsages.{FindUsagesHandler, FindUsagesHandlerFactory}
import com.intellij.find.impl.FindManagerImpl
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.compiler.{CompileStatusNotification, CompilerManager}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.{PsiElement, PsiNamedElement}
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.findUsages.compilerReferences.ScalaImplicitMemberUsageSearcher._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.{CompilerReferenceIndexingStatusListener, CompilerReferenceIndexingTopics, IndexerFailure}
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
        val maybeSuper = suggestChooseSuper(unwrapped)
        maybeSuper.filter {
          case ImplicitSearchTarget(target) => doBeforeImplicitSearchAction(target)
          case _                            => true
        }
      } else Option(unwrapped)

    replaced match {
      case Some(e) => new ScalaFindUsagesHandler(e, this)
      case None    => FindUsagesHandler.NULL_HANDLER
    }
  }

  private[this] def buildScopeBeforeImplicitSearch(
    target:  PsiElement,
    modules: Seq[Module]
  ): Unit = {
    val manager    = CompilerManager.getInstance(project)
    val connection = project.getMessageBus.connect(project)

    connection.subscribe(CompilerReferenceIndexingTopics.indexingStatus, new CompilerReferenceIndexingStatusListener {
      override def onIndexingFinished(failure: Option[IndexerFailure]): Unit = {
        if (failure.isEmpty) {
          val findManager = FindManager.getInstance(project).asInstanceOf[FindManagerImpl]
          val handler = new ScalaFindUsagesHandler(target, self)

          val runnable: Runnable = () =>
            findManager.getFindUsagesManager.findUsages(
              handler.getPrimaryElements,
              handler.getSecondaryElements,
              handler,
              handler.getFindUsagesOptions(),
              false
            )

          TransactionGuard.getInstance().submitTransactionAndWait(runnable)
        }
        connection.disconnect()
      }
    })

    val scope = manager.createModulesCompileScope(modules.toArray, true)
    val notification: CompileStatusNotification =
      (aborted, errors, _, _)  => if (aborted || errors != 0) connection.disconnect()
    manager.make(scope, notification)
  }

  private[this] def doBeforeImplicitSearchAction(target: PsiNamedElement): Boolean =
    assertSearchScopeIsSufficient(target) match {
      case Some(BuildModules(modules)) =>
        buildScopeBeforeImplicitSearch(target, modules)
        false
      case Some(RebuildProject) =>
        val manager = CompilerManager.getInstance(project)
        manager.rebuild(null)
        false
      case Some(CancelSearch) => false
      case None               => true
    }

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
