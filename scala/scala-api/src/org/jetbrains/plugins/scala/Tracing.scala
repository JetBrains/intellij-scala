package org.jetbrains.plugins.scala

import com.intellij.lang.{ASTNode, PsiBuilder}
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

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

  def parser(builder: PsiBuilder): Unit = if (parameters.parser) {
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
