package org.jetbrains.plugins.scala.externalHighlighters

import java.io.File
import java.util.EventListener

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import org.apache.commons.lang3.StringUtils
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener}
import org.jetbrains.plugins.scala.externalHighlighters.UpdateCompilerGeneratedStateListener.CompilerGeneratedStateTopic
import org.jetbrains.plugins.scala.project.template.FileExt
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.externalHighlighters.ExternalHighlighting.Pos

private class UpdateCompilerGeneratedStateListener(project: Project)
  extends CompilerEventListener {

  import UpdateCompilerGeneratedStateListener.HandleEventResult
  
  override def eventReceived(event: CompilerEvent): Unit = {
    val oldState = CompilerGeneratedStateManager.get(project)

    val handleEventResult = event match {
      case CompilerEvent.CompilationStarted(_) =>
        val newHighlightOnCompilationFinished = oldState.toHighlightingState.collect {
          case (virtualFile, highlightings) if highlightings.nonEmpty => virtualFile
        }.toSet
        val newState = oldState.copy(highlightOnCompilationFinished = newHighlightOnCompilationFinished)
        Some(HandleEventResult(newState, Set.empty, informWolf = false))
      case CompilerEvent.MessageEmitted(compilationId, msg) =>
        for {
          text <- Option(msg.text)
          from <- Pos.fromPosInfo(msg.from)
          to = Pos.fromPosInfo(msg.to).getOrElse(from)
          source <- msg.source
          virtualFile <- source.toVirtualFile
          highlighting = ExternalHighlighting(
            highlightType = kindToHighlightInfoType(msg.kind, text),
            message = text,
            from = from,
            to = to
          )
          fileState = FileCompilerGeneratedState(compilationId, Set(highlighting))
          newState = replaceOrAppendFileState(oldState, virtualFile, fileState)
        } yield HandleEventResult(
          newState = newState,
          toHighlight = Set(virtualFile).filterNot(oldState.highlightOnCompilationFinished(_)),
          informWolf = false
        )
      case CompilerEvent.ProgressEmitted(_, progress) =>
        val newState = oldState.copy(progress = progress)
        Some(HandleEventResult(newState, Set.empty, informWolf = false))
      case CompilerEvent.CompilationFinished(compilationId, sources) =>
        val vFiles = for {
          source <- sources
          virtualFile <- source.toVirtualFile
        } yield virtualFile
        val emptyState = FileCompilerGeneratedState(compilationId, Set.empty)
        val newState = vFiles.foldLeft(oldState) { case (acc, file) =>
          replaceOrAppendFileState(acc, file, emptyState)
        }.copy(progress = 1.0, highlightOnCompilationFinished = Set.empty)
        val toHighlight = vFiles.filter(oldState.highlightOnCompilationFinished(_))
        Some(HandleEventResult(newState, toHighlight, informWolf = true))
      case _ =>
        None
    }
    
    handleEventResult.foreach { case HandleEventResult(newState, toHighlight, informWolf) =>
      CompilerGeneratedStateManager.update(project, newState)
      val highlightingState = newState.toHighlightingState
      updateHighlightings(toHighlight, highlightingState)
      if (informWolf) ExternalHighlighters.informWolf(project, highlightingState)
    }

    // for test purposes only
    event match {
      case CompilerEvent.CompilationFinished(_, sources) =>
        val publisher = project.getMessageBus.syncPublisher(CompilerGeneratedStateTopic)
        publisher.stateUpdated(sources)
      case _ =>
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
      StringUtils.startsWithIgnoreCase(text, "not found:") ||
      StringUtils.startsWithIgnoreCase(text, "cannot find symbol")

  private def replaceOrAppendFileState(oldState: CompilerGeneratedState,
                                       file: VirtualFile,
                                       fileState: FileCompilerGeneratedState): CompilerGeneratedState = {
    val newFileState = oldState.files.get(file) match {
      case Some(oldFileState) if oldFileState.compilationId == fileState.compilationId =>
        oldFileState.withExtraHighlightings(fileState.highlightings)
      case _ =>
        fileState
    }
    val newFileStates = oldState.files.updated(file, newFileState)
    oldState.copy(files = newFileStates)
  }

  private def updateHighlightings(virtualFiles: Set[VirtualFile], state: HighlightingState): Unit =
    for {
      editor <- EditorFactory.getInstance.getAllEditors
      editorProject <- Option(editor.getProject)
      if editorProject == project
      vFile <- editor.getDocument.virtualFile
      if virtualFiles contains vFile
    } ExternalHighlighters.applyHighlighting(project, editor, state)
}

object UpdateCompilerGeneratedStateListener {

  private case class HandleEventResult(newState: CompilerGeneratedState,
                                       toHighlight: Set[VirtualFile],
                                       informWolf: Boolean)
  
  @TestOnly
  trait CompilerGeneratedStateTopicListener extends EventListener {
    def stateUpdated(sources: Set[File]): Unit
  }
  val CompilerGeneratedStateTopic: Topic[CompilerGeneratedStateTopicListener] = Topic.create(
    "compiler-generated-state-update",
    classOf[CompilerGeneratedStateTopicListener]
  )
}
