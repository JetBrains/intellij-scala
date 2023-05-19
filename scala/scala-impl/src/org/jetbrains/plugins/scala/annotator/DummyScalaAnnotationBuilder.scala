package org.jetbrains.plugins.scala.annotator
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.{LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.{HighlightSeverity, ProblemGroup}
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.{GutterIconRenderer, TextAttributes}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls

/**
 * To be used in tests and annotator-based inspection, where no instance of AnnotationHolder is available
 */
abstract class DummyScalaAnnotationBuilder(severity: HighlightSeverity, @Nls message: String)
  extends ScalaAnnotationBuilder {

  private var rangeTransformer: TextRange => TextRange = identity
  private var range: TextRange = _
  private var enforcedAttributes: TextAttributesKey = _

  override def setRangeTransformer(transformer: TextRange => TextRange): this.type = {
    rangeTransformer = transformer
    this
  }

  override def range(range: TextRange): this.type = {
    this.range = range
    this
  }

  override def range(element: ASTNode): this.type = {
    this.range = element.getTextRange
    this
  }

  override def range(element: PsiElement): this.type = {
    this.range = element.getTextRange
    this
  }

  override def textAttributes(enforcedAttributes: TextAttributesKey): this.type = {
    this.enforcedAttributes = enforcedAttributes
    this
  }

  def onCreate(severity: HighlightSeverity, @Nls message: String, range: TextRange, enforcedAttributes: TextAttributesKey): Unit

  override def create(): Unit =
    onCreate(severity, message, rangeTransformer(range), enforcedAttributes)

  override def highlightType(highlightType: ProblemHighlightType): this.type = this
  override def afterEndOfLine: this.type = this
  override def fileLevel: this.type = this
  override def gutterIconRenderer(gutterIconRenderer: GutterIconRenderer): this.type = this
  override def problemGroup(problemGroup: ProblemGroup): this.type = this
  override def enforcedTextAttributes(enforcedAttributes: TextAttributes): this.type = this
  override def tooltip(tooltip: String): this.type = this
  override def needsUpdateOnTyping: this.type = this
  override def needsUpdateOnTyping(value: Boolean): this.type = this
  override def withFix(fix: IntentionAction): this.type = this
  override def newFix(fix: IntentionAction): ScalaAnnotationBuilder.FixBuilder = DummyFixBuilder
  override def newLocalQuickFix(fix: LocalQuickFix, problemDescriptor: ProblemDescriptor): ScalaAnnotationBuilder.FixBuilder = DummyFixBuilder

  private object DummyFixBuilder extends ScalaAnnotationBuilder.FixBuilder {
    override def range(range: TextRange): ScalaAnnotationBuilder.FixBuilder = this
    override def key(key: HighlightDisplayKey): ScalaAnnotationBuilder.FixBuilder = this
    override def batch: ScalaAnnotationBuilder.FixBuilder = this
    override def universal: ScalaAnnotationBuilder.FixBuilder = this
    override def registerFix: DummyScalaAnnotationBuilder.this.type = DummyScalaAnnotationBuilder.this
  }
}
