package org.jetbrains.plugins.scala

import com.intellij.lang.ASTNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression.ExpressionTypeResult
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.types.{ConstraintsResult, ScType}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

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

  def resolve(reference: ScReference): Unit = {
    traceInEditor(reference, "Resolve", start = true)
  }

  def resolve(reference: ScReference, result: Array[ScalaResolveResult]): Unit = {
    if (parameters.resolve) {
      trace("Resolve: " + reference.asText + " → " + result.map(_.asText).mkString(", "))
    }
    traceInEditor(reference, "Resolve")
  }

  def inference(expression: ScExpression): Unit = {
    traceInEditor(expression, "Inference", start = true)
  }

  def inference(expression: ScExpression, result: ExpressionTypeResult): Unit = {
    if (parameters.inference) {
      trace("Inference: " + expression.asText + " → " + result.asText)
    }
    traceInEditor(expression, "Inference")
  }

  /** Whether resolve and type inference are traced in the editor, see [[ElementTracer]] */
  def isHighlightingTracingEnabled: Boolean = Registry.is("scala.highlighting.tracing")

  private def traceInEditor(element: PsiElement, reason: String, start: Boolean = false): Unit =
    if (isHighlightingTracingEnabled) {
      ElementTracer.instance.trace(element, reason, start)
    }

  def equivalence(left: ScType, right: ScType, result: ConstraintsResult): Unit = {
    if (parameters.equivalence) {
      trace("Equivalence: (" + left + ", " + right + ") → " + result)
    }
  }

  def conformance(left: ScType, right: ScType, result: ConstraintsResult): Unit = {
    if (parameters.conformance) {
      trace("Conformance: (" + left + ", " + right + ") → " + result)
    }
  }

  private var parameters: Parameters = Parameters.Empty

  private var lines = ListBuffer.empty[String]

  private var annotatedElements = ListBuffer.empty[PsiElement]

  /** The parameters of the current [[TracingSession]] */
  private[scala] def currentParameters: Parameters = parameters

  private[scala] def startSession(parameters: Parameters): Unit = {
    lines = ListBuffer.empty[String]
    this.parameters = parameters
  }

  /** @return the traced lines */
  private[scala] def stopSession(): String = {
    this.parameters = Parameters.Empty
    lines.mkString("\n")
  }

  private[scala] def startAnnotating(): Unit =
    annotatedElements = ListBuffer.empty[PsiElement]

  private[scala] def annotated: Seq[PsiElement] = annotatedElements.toSeq

  def trace(s: String): Unit = synchronized {
    if (s.contains(parameters.filter) && (!parameters.coalesce || !lines.lastOption.contains(s))) {
      lines += s
    }
  }

  private[scala] implicit class PsiElementExt(private val that: PsiElement) {
    def asText: String = that.getText.showWhitespaces
  }

  private implicit class ASTNodeExt(private val that: ASTNode) {
    def asText: String = that.getText.showWhitespaces
  }

  private[scala] implicit class CharSequenceExt(private val that: CharSequence) {
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
                        equivalence: Boolean = false,
                        conformance: Boolean = false,
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
        equivalence = true,
        conformance = true,
        coalesce = false,
        filter = ""
      )
  }
}

/**
 * Shows traced resolve and type inference in the editor.
 * Its implementation, [[incremental.Tracing.EditorTracer]], is editor code, so the core knows only this interface.
 */
@ApiStatus.Internal
trait ElementTracer {
  def trace(element: PsiElement, reason: String, start: Boolean): Unit
}

object ElementTracer {
  def instance: ElementTracer = ApplicationManager.getApplication.getService(classOf[ElementTracer])
}
