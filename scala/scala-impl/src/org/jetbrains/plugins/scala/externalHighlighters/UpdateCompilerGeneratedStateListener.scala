package org.jetbrains.plugins.scala.externalHighlighters

import java.io.File
import java.util.EventListener

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import org.apache.commons.lang3.StringUtils
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener}
import org.jetbrains.plugins.scala.externalHighlighters.UpdateCompilerGeneratedStateListener.CompilerGeneratedStateTopic
import org.jetbrains.plugins.scala.project.template.FileExt

private class UpdateCompilerGeneratedStateListener(project: Project)
  extends CompilerEventListener {

  override def eventReceived(event: CompilerEvent): Unit = {
    val newState = Option(event).flatMap {
      case CompilerEvent.MessageEmitted(compilationId, msg) =>
        for {
          text <- Option(msg.text)
          line <- msg.line
          column <- msg.column
          source <- msg.source
          virtualFile <- source.toVirtualFile
          highlighting = ExternalHighlighting(
            highlightType = kindToHighlightInfoType(msg.kind, text),
            message = text,
            fromLine = line.toInt,
            fromColumn = column.toInt,
            toLine = msg.toLine.map(_.toInt),
            toColumn = msg.toColumn.map(_.toInt)
          )
          fileState = FileCompilerGeneratedState(compilationId, Set(highlighting))
        } yield replaceOrAppendFileState(virtualFile, fileState)
      case CompilerEvent.CompilationFinished(compilationId, sources) =>
        val vFiles = for {
          source <- sources
          virtualFile <- source.toVirtualFile
        } yield virtualFile
        val emptyState = FileCompilerGeneratedState(compilationId, Set.empty)
        Some(replaceOrAppendFileState(vFiles, emptyState))
      case CompilerEvent.ProgressEmitted(_, progress) =>
        Some(updateProgress(progress))
      case _ =>
        None
    }

    newState.foreach { state =>
      CompilerGeneratedStateManager.update(project, state)
    }

    event match {
      case CompilerEvent.CompilationFinished(_, sources) if ApplicationManager.getApplication.isUnitTestMode =>
        val publisher = project.getMessageBus.syncPublisher(CompilerGeneratedStateTopic)
        publisher.stateUpdated(sources)
      case _                                                                                                 =>
    }
  }

  private def kindToHighlightInfoType(kind: Kind, text: String): HighlightInfoType = kind match {
    case Kind.ERROR if isErrorMessageAboutWrongRef(text) =>
      HighlightInfoType.WRONG_REF
    case Kind.ERROR =>
      HighlightInfoType.ERROR
    case Kind.WARNING =>
      HighlightInfoType.WARNING
    case Kind.INFO =>
      HighlightInfoType.WEAK_WARNING
    case _ =>
      HighlightInfoType.INFORMATION
  }
  
  private def isErrorMessageAboutWrongRef(text: String): Boolean =
    StringUtils.startsWithIgnoreCase(text, "value") && text.contains("is not a member of") ||
    StringUtils.startsWithIgnoreCase(text, "not found:")

  private def replaceOrAppendFileState(files: Traversable[VirtualFile], fileState: FileCompilerGeneratedState): CompilerGeneratedState = {
    val oldState = CompilerGeneratedStateManager.get(project)
    val oldFileStates = oldState.files
    val newFileStates = files.foldLeft(oldFileStates) { case (acc, file) =>
      val newFileState = acc.get(file) match {
        case Some(oldFileState) if oldFileState.compilationId == fileState.compilationId =>
          oldFileState.withExtraHighlightings(fileState.highlightings)
        case _ =>
          fileState
      }
      acc.updated(file, newFileState)
    }
    oldState.copy(files = newFileStates)
  }

  private def replaceOrAppendFileState(file: VirtualFile, fileState: FileCompilerGeneratedState): CompilerGeneratedState =
    replaceOrAppendFileState(Some(file), fileState)

  private def updateProgress(progress: Double): CompilerGeneratedState =
    CompilerGeneratedStateManager.get(project).copy(progress = progress)
}

object UpdateCompilerGeneratedStateListener {

  @TestOnly
  trait CompilerGeneratedStateTopicListener extends EventListener {
    def stateUpdated(sources: Set[File]): Unit
  }
  val CompilerGeneratedStateTopic: Topic[CompilerGeneratedStateTopicListener] = Topic.create(
    "compiler-generated-state-update",
    classOf[CompilerGeneratedStateTopicListener]
  )
}
