package org.jetbrains.plugins.scala

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DaemonListener
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.{BulkAwareDocumentListener, DocumentEvent}
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiManager, PsiTreeChangeAdapter, PsiTreeChangeEvent}
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.Tracing.{CharSequenceExt, Parameters, PsiElementExt, currentParameters, trace}

import java.util as ju
import scala.annotation.nowarn

/**
 * Collects the [[Tracing]] output of an action, together with the PSI, document and highlighting events it causes.
 * The listeners are editor and highlighting code, so they are not part of [[Tracing]], which the core calls.
 */
@ApiStatus.Internal
object TracingSession {
  private val psiTreeChangeListener = new PsiTreeChangeAdapter {
    override def childAdded(event: PsiTreeChangeEvent): Unit = if (currentParameters.psi) {
      trace(s"PSI added: ${event.getChild.asText}")
    }

    override def childRemoved(event: PsiTreeChangeEvent): Unit = if (currentParameters.psi) {
      trace(s"PSI removed: ${event.getChild.asText}")
    }

    override def childReplaced(event: PsiTreeChangeEvent): Unit = if (currentParameters.psi) {
      trace(s"PSI change: ${event.getOldChild.asText} → ${event.getNewChild.asText}")
    }
  }

  private val documentListener = new BulkAwareDocumentListener {
    override def documentChanged(event: DocumentEvent): Unit = if (currentParameters.document) {
      trace(s"Document change: ${event.getOldFragment.showWhitespaces} → ${event.getNewFragment.showWhitespaces}")
    }
  }

  private val daemonListener = new DaemonListener {
    override def daemonStarting(fileEditors: ju.Collection[? <: FileEditor]): Unit = if (currentParameters.annotator) {
      trace("Highlighting started")
      Tracing.startAnnotating()
    }

    override def daemonFinished(fileEditors: ju.Collection[? <: FileEditor]): Unit = if (currentParameters.annotator) {
      trace(s"Annotated: ${Tracing.annotated.map(_.asText).mkString(" ")}")
      trace("Highlighting finished")
    }
  }

  def tracing(project: Project, parameters: Parameters)(action: => Unit): String = {
    val psiManager = PsiManager.getInstance(project)
    val eventMulticaster = EditorFactory.getInstance.getEventMulticaster
    val connection = project.getMessageBus.connect(project)

    psiManager.addPsiTreeChangeListener(psiTreeChangeListener, project)
    eventMulticaster.addDocumentListener(documentListener, project)
    connection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, daemonListener)

    Tracing.startSession(parameters)

    var traced = ""
    try {
      action
    } finally {
      traced = Tracing.stopSession()

      psiManager.removePsiTreeChangeListener(psiTreeChangeListener): @nowarn("cat=deprecation")
      eventMulticaster.removeDocumentListener(documentListener)
      connection.disconnect()
    }
    traced
  }
}
