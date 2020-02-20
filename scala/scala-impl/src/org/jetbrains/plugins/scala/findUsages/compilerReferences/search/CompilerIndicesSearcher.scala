package org.jetbrains.plugins.scala.findUsages.compilerReferences.search

import com.intellij.notification.{Notification, NotificationType, Notifications}
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiManager}
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.findUsages.compilerReferences.search.UsageToPsiElements._
import org.jetbrains.plugins.scala.findUsages.compilerReferences.{Timestamped, UsagesInFile}

abstract class CompilerIndicesSearcher[Target, Result, Params](reqireReadAction: Boolean)
  extends QueryExecutorBase[Result, Params](reqireReadAction) {

  protected def processResultsFromCompilerService(
    target:    Target,
    results:   Set[Timestamped[UsagesInFile]],
    project:   Project,
    processor: Processor[_ >: Result]
  ): Unit = {
    val fileDocManager     = FileDocumentManager.getInstance()
    val outdated           = Set.newBuilder[String]
    val psiManager         = PsiManager.getInstance(project)
    val psiDocumentManager = PsiDocumentManager.getInstance(project)

    results.foreach { usage =>
      val maybeCandidates = extractCandidatesFromUsage(psiManager, psiDocumentManager, usage.unwrap)

      maybeCandidates.foreach { case candidates @ ElementsInContext(_, file, doc) =>
        val isOutdated = fileDocManager.isDocumentUnsaved(doc) ||
          file.getVirtualFile.getTimeStamp > usage.timestamp

        val shouldMarkAsOutdated = !processMatchingElements(
          target,
          usage.unwrap,
          isOutdated,
          candidates,
          processor
        )

        if (isOutdated && shouldMarkAsOutdated)
          outdated += file.getVirtualFile.getPresentableName
      }
    }

    val filesToNotify = outdated.result()

    if (filesToNotify.nonEmpty) {
      Notifications.Bus.notify(
        new Notification(
          ScalaBundle.message("bytecode.indices.find.usages"),
          ScalaBundle.message("bytecode.indices.invalidated.title"),
          ScalaBundle.message("bytecode.indices.invalidated.message", filesToNotify.mkString(",")),
          NotificationType.WARNING
        )
      )
    }
  }

  /**
    * Processes elements associated with a single [[UsagesInFile]] instance,
    * and returns true if a corresponding element has been found for each an every line in
    * a usage, false otherwise.
    */
  protected def processMatchingElements(
    target:             Target,
    usage:              UsagesInFile,
    isPossiblyOutdated: Boolean,
    elements:           ElementsInContext,
    processor:          Processor[_ >: Result]
  ): Boolean
}
