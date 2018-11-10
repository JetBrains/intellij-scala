package org.jetbrains.plugins.scala.findUsages.compilerReferences
package search

import java.awt.{List => _}

import com.intellij.find.FindManager
import com.intellij.find.impl.FindManagerImpl
import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.application.{QueryExecutorBase, TransactionGuard}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.{DialogWrapper, Messages}
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.task.{ProjectTaskManager, ProjectTaskNotification}
import com.intellij.util.Processor
import javax.swing._
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.compilation.CompilerMode
import org.jetbrains.plugins.scala.findUsages.compilerReferences.indices.ScalaCompilerIndices
import org.jetbrains.plugins.scala.findUsages.compilerReferences.search.ImplicitUsagesSearchDialogs._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.settings.CompilerIndicesSettings
import org.jetbrains.plugins.scala.findUsages.factory.{ScalaFindUsagesHandler, ScalaFindUsagesHandlerFactory}
import org.jetbrains.plugins.scala.util.ImplicitUtil._
import org.jetbrains.sbt.shell.SbtShellCommunication

import scala.collection.JavaConverters._

private class ImplicitMemberUsageSearcher
    extends QueryExecutorBase[PsiReference, ImplicitReferencesSearch.SearchParameters](true)
    with UsageToPsiElements {

  override def processQuery(
    parameters: ImplicitReferencesSearch.SearchParameters,
    consumer:   Processor[_ >: PsiReference]
  ): Unit = {
    val target  = parameters.element
    val project = target.getProject
    val service = ScalaCompilerReferenceService(project)
    val usages  = service.usagesOf(target)
    val refs    = extractUsages(target, usages)
    refs.foreach(consumer.process)
  }

  private[this] def extractUsages(target: PsiElement, usages: Set[Timestamped[UsagesInFile]]): Seq[PsiReference] = {
    val project        = target.getProject
    val fileDocManager = FileDocumentManager.getInstance()
    val outdated       = Set.newBuilder[String]

    def extractReferences(usage: Timestamped[UsagesInFile]): Seq[PsiReference] =
      (for {
        ElementsInContext(elements, file, doc) <- extractCandidatesFromUsage(project, usage.unwrap)
      } yield {
        val isOutdated = fileDocManager.isDocumentUnsaved(doc) ||
          file.getVirtualFile.getTimeStamp > usage.timestamp

        val lineNumber = (e: PsiElement) => doc.getLineNumber(e.getTextOffset) + 1

        val refs       = elements.flatMap(target.refOrImplicitRefIn).toList
        val extraLines = usage.unwrap.lines.diff(refs.map(r => lineNumber(r.getElement)))

        val unresolvedRefs = extraLines.flatMap { line =>
          val offset = doc.getLineStartOffset(line - 1)
          if (!isOutdated) UnresolvedImplicitReference(target, file, offset).toOption
          else {
            outdated += file.getVirtualFile.getPresentableName
            None
          }
        }

        refs ++ unresolvedRefs
      }).getOrElse(Seq.empty)

    val result        = usages.flatMap(extractReferences)(collection.breakOut)
    val filesToNotify = outdated.result()

    if (filesToNotify.nonEmpty) {
      Notifications.Bus.notify(
        new Notification(
          ScalaBundle.message("find.usages.implicit.dialog.title"),
          "Implicit Usages Invalidated",
          s"Some usages in the following files may have been invalidated, due to external changes: ${filesToNotify.mkString(",")}.",
          NotificationType.WARNING
        )
      )
    }
    result
  }
}

object ImplicitMemberUsageSearcher {
  private[findUsages] sealed trait BeforeImplicitSearchAction {
    def runAction(): Boolean
  }

  private[findUsages] case object CancelSearch extends BeforeImplicitSearchAction {
    override def runAction(): Boolean = false
  }

  private[findUsages] final case class RebuildProject(project: Project) extends BeforeImplicitSearchAction {
    override def runAction(): Boolean = {
      ScalaCompilerReferenceService(project).inTransaction {
        case (CompilerMode.JPS, _) => ProjectTaskManager.getInstance(project).rebuildAllModules()
        case (CompilerMode.SBT, _) =>
          // there is no need to do a full rebuild with sbt project as
          // we can simply fetch info about ALL classes instead of just
          // the ones built incrementally via incrementalityType setting

          val shell = SbtShellCommunication.forProject(project)
          shell.command("rebuildIdeaIndices")
      }

      false
    }
  }

  private[findUsages] final case class BuildModules(
    target:  PsiElement,
    project: Project,
    modules: Set[Module]
  ) extends BeforeImplicitSearchAction {
    override def runAction(): Boolean = if (modules.nonEmpty) {
      val manager    = ProjectTaskManager.getInstance(project)
      val connection = project.getMessageBus.connect(project)

      connection.subscribe(CompilerReferenceServiceStatusListener.topic, new CompilerReferenceServiceStatusListener {
        override def onIndexingFinished(): Unit = {
          val findManager = FindManager.getInstance(project).asInstanceOf[FindManagerImpl]
          val handler     = new ScalaFindUsagesHandler(target, ScalaFindUsagesHandlerFactory.getInstance(project))

          val runnable: Runnable = () =>
            findManager.getFindUsagesManager.findUsages(
              handler.getPrimaryElements,
              handler.getSecondaryElements,
              handler,
              handler.getFindUsagesOptions(),
              false
            )

          TransactionGuard.getInstance().submitTransactionAndWait(runnable)
          connection.disconnect()
        }
      })

      val notification: ProjectTaskNotification =
        result => if (result.isAborted || result.getErrors != 0) connection.disconnect()

      manager.build(modules.toArray, notification)
      false
    } else true
  }

  private[findUsages] def assertSearchScopeIsSufficient(target: PsiNamedElement): Option[BeforeImplicitSearchAction] = {
    val project = target.getProject
    val service = ScalaCompilerReferenceService(project)

    if (!CompilerIndicesSettings(project).indexingEnabled) {
      inEventDispatchThread(new EnableCompilerIndicesDialog(project, canBeParent = false).show())
      Option(CancelSearch)
    } else if (service.isIndexingInProgress) {
      inEventDispatchThread(showIndexingInProgressDialog(project))
      Option(CancelSearch)
    } else {
      val (dirtyModules, upToDateModules) = dirtyModulesInDependencyChain(target)
      val validIndexExists                = upToDateCompilerIndexExists(project, ScalaCompilerIndices.version)

      if (dirtyModules.nonEmpty || !validIndexExists) {
        var action: Option[BeforeImplicitSearchAction] = None

        val dialogAction =
          () =>
            action = Option(
              showRebuildSuggestionDialog(project, dirtyModules, upToDateModules, validIndexExists, target)
          )

        inEventDispatchThread(dialogAction())
        action
      } else None
    }
  }

  private[this] def inEventDispatchThread[T](body: => T): Unit =
    if (SwingUtilities.isEventDispatchThread) body
    else                                      invokeAndWait(body)

  private[this] def dirtyModulesInDependencyChain(element: PsiElement): (Set[Module], Set[Module]) = {
    val project          = element.getProject
    val file             = PsiTreeUtil.getContextOfType(element, classOf[PsiFile]).getVirtualFile
    val index            = ProjectFileIndex.getInstance(project)
    val modules          = index.getOrderEntriesForFile(file).asScala.map(_.getOwnerModule).toSet
    val dirtyScopeHolder = ScalaCompilerReferenceService(project).getDirtyScopeHolder
    val dirtyScopes      = dirtyScopeHolder.dirtyScope
    modules.partition(dirtyScopes.isSearchInModuleContent)
  }

  private[this] def showIndexingInProgressDialog(project: Project): Unit = {
    val message = "Implicit usages search is unavailable during bytecode indexing."
    Messages.showInfoMessage(project, message, "Indexing In Progress")
  }

  private[this] def showRebuildSuggestionDialog(
    project:          Project,
    dirtyModules:     Set[Module],
    upToDateModules:  Set[Module],
    validIndexExists: Boolean,
    element:          PsiNamedElement
  ): BeforeImplicitSearchAction = {
    import DialogWrapper.{CANCEL_EXIT_CODE, OK_EXIT_CODE}

    val dialog = new ImplicitFindUsagesDialog(
      false,
      dirtyModules,
      upToDateModules,
      validIndexExists,
      element
    )
    dialog.show()

    dialog.getExitCode match {
      case OK_EXIT_CODE if !validIndexExists => RebuildProject(project)
      case OK_EXIT_CODE                      => BuildModules(element, project, dialog.moduleSelection)
      case CANCEL_EXIT_CODE                  => CancelSearch
    }
  }
}
