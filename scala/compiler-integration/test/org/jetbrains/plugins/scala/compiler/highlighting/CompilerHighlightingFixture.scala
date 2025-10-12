package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.daemon.impl.{DaemonCodeAnalyzerImpl, HighlightInfo}
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.compiler.CompilerEvent.CompilationHighlightingFinalStageFinished
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, invokeAndWait}
import org.jetbrains.plugins.scala.project.VirtualFileExt

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Promise}
import scala.jdk.CollectionConverters.CollectionHasAsScala

final class CompilerHighlightingFixture(project: Project) {

  private var isFinalHighlightingCompilerEvent: (VirtualFile, CompilerEvent) => Boolean =
    (_, event) => event.is[CompilationHighlightingFinalStageFinished]

  def setFinalHighlightingCompilerEventChecker(checker: (VirtualFile, CompilerEvent) => Boolean): Unit = {
    isFinalHighlightingCompilerEvent = checker
  }

  def fetchHighlightInfos(virtualFile: VirtualFile): Seq[HighlightInfo] = invokeAndWait {
    val document = virtualFile.findDocument.get
    DaemonCodeAnalyzerImpl.getHighlights(document, null, project).asScala.toSeq
  }

  def openFileAndWaitUntilFileIsHighlighted(virtualFile: VirtualFile): Unit = {
    val finalCompilationEventPromise = listenForFinalCompilerEventPromise(virtualFile)
    // we need to create the listener before triggering compilation, otherwise the even can be broadcasted before we subscribe to it
    val highlightingAppliedPromise = externalHighlightingAppliedPromise(virtualFile)

    triggerCompilerBasedHighlightingByOpeningTheFile(virtualFile)

    val timeout = 60.seconds
    Await.result(finalCompilationEventPromise.future, timeout)
    Await.result(highlightingAppliedPromise.future, timeout)
  }

  private def listenForFinalCompilerEventPromise(virtualFile: VirtualFile): Promise[Unit] = {
    val promise = Promise[Unit]()
    project.getMessageBus.connect().subscribe(CompilerEventListener.topic, new CompilerEventListener {
      override def eventReceived(event: CompilerEvent): Unit = {
        if (isFinalHighlightingCompilerEvent(virtualFile, event)) {
          promise.success(())
        }
      }
    })
    promise
  }

  // Compilation is done on file opening (see RegisterCompilationListener.MyFileEditorManagerListener)
  private def triggerCompilerBasedHighlightingByOpeningTheFile(virtualFile: VirtualFile): Unit = {
    invokeAndWait {
      val descriptor = new OpenFileDescriptor(project, virtualFile)
      val editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
      // The tests are running in a headless environment where focus events are not propagated.
      // We need to call our listener manually.
      new CompilerHighlightingEditorFocusListener(editor).focusGained()
    }
  }

  private def externalHighlightingAppliedPromise(virtualFile: VirtualFile): Promise[Unit] = {
    val promise = Promise[Unit]()
    project.getMessageBus.connect().subscribe(ExternalHighlightingAppliedListener.topic, new ExternalHighlightingAppliedListener {
      override def highlightingApplied(virtualFiles: Set[VirtualFile]): Unit = {
        if (virtualFiles.contains(virtualFile)) {
          promise.success(())
        }
      }
    })
    promise
  }
}
