package org.jetbrains.plugins.scala

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DaemonListener
import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.{BulkAwareDocumentListener, DocumentEvent}
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiManager, PsiTreeChangeAdapter, PsiTreeChangeEvent}
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression.ExpressionTypeResult
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

import java.util
import scala.collection.mutable.ListBuffer

@ApiStatus.Internal
object Tracing {
  def highlightingLexerStart(text: CharSequence, startOffset: Int, endOffset: Int, initialState: Int): Unit = if (parameters.lexer) {
    trace(s"Highlighting lexer: ${text.subSequence(startOffset, endOffset).showWhitespaces}")
  }

  def lexerStart(text: CharSequence, startOffset: Int, endOffset: Int, initialState: Int): Unit = if (parameters.lexer) {
    trace(s"Lexer: ${text.subSequence(startOffset, endOffset).showWhitespaces}")
  }

  def lexer(text: CharSequence, tokenStart: Int, tokenEnd: Int): Unit = if (parameters.lexer) {

  }

  def parser(builder: ScalaPsiBuilder): Unit = if (parameters.parser) {
    trace(s"Parser: ${builder.getTreeBuilt.asText}")
  }

  def locality(element: PsiElement): Unit = if (parameters.locality) {
    trace(s"Change locality: ${element.asText}")
  }

  def annotator(e: PsiElement): Unit = if (parameters.annotator) {
    annotatedElements += e
  }

  def modification(tracker: String, count: Long): Unit = if (parameters.modification) {
//    trace(s"Modification $tracker: $count")
  }

  def resolve(reference: ScReference, result: Array[ScalaResolveResult]): Unit = if (parameters.resolve) {
    trace("Resolve: " + reference.asText + " → " + result.map(_.asText).mkString(", "))
  }

  def inference(expression: ScExpression, result: ExpressionTypeResult): Unit = if (parameters.inference) {
    trace("Inference: " + expression.asText + " → " + result.asText)
  }

  private val psiTreeChangeListener = new PsiTreeChangeAdapter {
    override def childAdded(event: PsiTreeChangeEvent): Unit = if (parameters.psi) {
      trace(s"PSI added: ${event.getChild.asText}")
    }

    override def childRemoved(event: PsiTreeChangeEvent): Unit = if (parameters.psi) {
      trace(s"PSI removed: ${event.getChild.asText}")
    }

    override def childReplaced(event: PsiTreeChangeEvent): Unit = if (parameters.psi) {
      trace(s"PSI change: ${event.getOldChild.asText} → ${event.getNewChild.asText}")
    }
  }

  private val documentListener = new BulkAwareDocumentListener {
    override def documentChanged(event: DocumentEvent): Unit = if (parameters.document) {
      trace(s"Document change: ${event.getOldFragment.showWhitespaces} → ${event.getNewFragment.showWhitespaces}")
    }
  }

  private val daemonListener = new DaemonListener {
    override def daemonStarting(fileEditors: util.Collection[_ <: FileEditor]): Unit = if (parameters.annotator) {
      trace("Highlighting started")
      annotatedElements = ListBuffer.empty[PsiElement]
    }

    override def daemonFinished(fileEditors: util.Collection[_ <: FileEditor]): Unit = if (parameters.annotator) {
      trace(s"Annotated: ${annotatedElements.map(_.asText).mkString(" ")}")
      trace("Highlighting finished")
    }
  }

  private var parameters: Parameters = Parameters.Empty

  private var lines = ListBuffer.empty[String]

  private var annotatedElements = ListBuffer.empty[PsiElement]

  def tracing(project: Project, parameters: Parameters)(action: => Unit): String = {
    val psiManager = PsiManager.getInstance(project)
    val eventMulticaster = EditorFactory.getInstance.getEventMulticaster
    val connection = project.getMessageBus.connect(project)

    psiManager.addPsiTreeChangeListener(psiTreeChangeListener, project)
    eventMulticaster.addDocumentListener(documentListener, project)
    connection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, daemonListener)

    lines = ListBuffer.empty[String]

    this.parameters = parameters

    try {
      action
    } finally {
      this.parameters = Parameters.Empty

      psiManager.removePsiTreeChangeListener(psiTreeChangeListener)
      eventMulticaster.removeDocumentListener(documentListener)
      connection.disconnect()
    }

    lines.mkString("\n")
  }

  def trace(s: String): Unit = synchronized {
    if (s.contains(parameters.filter) && (!parameters.coalesce || !lines.lastOption.contains(s))) {
      lines += s
    }
  }

  private implicit class PsiElementExt(private val that: PsiElement) {
    def asText: String = that.getText.showWhitespaces
  }

  private implicit class ASTNodeExt(private val that: ASTNode) {
    def asText: String = that.getText.showWhitespaces
  }

  private implicit class CharSequenceExt(private val that: CharSequence) {
    def showWhitespaces: String = that.toString.replace(' ', '·').replace('\n', '↵')
  }

  private implicit class ScalaResolveResultExt(private val that: ScalaResolveResult) {
    def asText: String = that.element match {
      case member: ScMember =>
        val qualifier = Option(member.containingClass).map(_.qualifiedName)
          .orElse(member.getContext.asOptionOf[ScPackaging].map(_.fullPackageName))
        (qualifier ++ Iterable(member.name)).mkString(".")
      case _ => "—"
    }
  }

  private implicit class ExpressionTypeResultExt(private val that: ExpressionTypeResult) {
    def asText: String = that.tr.toOption.map(preciseCanonicalTextOf).getOrElse("—")

    private def preciseCanonicalTextOf(t: ScType): String = {
      ScalaApplicationSettings.getInstance.PRECISE_TEXT = true
      try {
        t.canonicalText.replace("_root_.", "")
      } finally {
        ScalaApplicationSettings.getInstance.PRECISE_TEXT = false
      }
    }
  }

  case class Parameters(document: Boolean = false,
                        lexer: Boolean = false,
                        parser: Boolean = false,
                        locality: Boolean = false,
                        psi: Boolean = false,
                        annotator: Boolean = false,
                        modification: Boolean = false,
                        resolve: Boolean = false,
                        inference: Boolean = false,
                        coalesce: Boolean = false,
                        filter: String = "")

  object Parameters {
    val Empty = Parameters()

    val Default =
      Parameters(
        document = true,
        lexer = true,
        parser = true,
        locality = true,
        psi = true,
        annotator = true,
        modification = false,
        resolve = true,
        inference = true,
        coalesce = false,
        filter = ""
      )
  }
}
