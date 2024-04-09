package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.intention.CommonIntentionAction
import com.intellij.codeInspection.{LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.{AnnotationBuilder, ProblemGroup}
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.{GutterIconRenderer, TextAttributes}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement


class ScalaAnnotationBuilderAdapter(annotationBuilder: AnnotationBuilder)
  extends ScalaAnnotationBuilder {

  private var rangeTransformer: TextRange => TextRange = identity

  override def setRangeTransformer(transformer: TextRange => TextRange): this.type = {
    rangeTransformer = transformer
    this
  }

  override def range(range: TextRange): this.type = {
    annotationBuilder.range(rangeTransformer(range))
    this
  }

  override def range(element: ASTNode): this.type = {
    annotationBuilder.range(rangeTransformer(element.getTextRange))
    this
  }

  override def range(element: PsiElement): this.type = {
    annotationBuilder.range(rangeTransformer(element.getTextRange))
    this
  }

  override def afterEndOfLine: this.type = {
    annotationBuilder.afterEndOfLine()
    this
  }

  override def fileLevel: this.type = {
    annotationBuilder.fileLevel()
    this
  }

  override def gutterIconRenderer(gutterIconRenderer: GutterIconRenderer): this.type = {
    annotationBuilder.gutterIconRenderer(gutterIconRenderer)
    this
  }

  override def problemGroup(problemGroup: ProblemGroup): this.type = {
    annotationBuilder.problemGroup(problemGroup)
    this
  }

  override def enforcedTextAttributes(enforcedAttributes: TextAttributes): this.type = {
    annotationBuilder.enforcedTextAttributes(enforcedAttributes)
    this
  }

  override def textAttributes(enforcedAttributes: TextAttributesKey): this.type = {
    annotationBuilder.textAttributes(enforcedAttributes)
    this
  }

  override def highlightType(highlightType: ProblemHighlightType): this.type = {
    annotationBuilder.highlightType(highlightType)
    this
  }

  override def tooltip(tooltip: String): this.type = {
    annotationBuilder.tooltip(tooltip)
    this
  }

  override def needsUpdateOnTyping: this.type = {
    annotationBuilder.needsUpdateOnTyping()
    this
  }

  override def needsUpdateOnTyping(value: Boolean): this.type = {
    annotationBuilder.needsUpdateOnTyping(value)
    this
  }

  override def withFix(fix: CommonIntentionAction): this.type = {
    annotationBuilder.withFix(fix)
    this
  }

  override def newFix(fix: CommonIntentionAction): ScalaAnnotationBuilder.FixBuilder =
    annotationBuilder.newFix(fix)

  override def newLocalQuickFix(fix: LocalQuickFix, problemDescriptor: ProblemDescriptor): ScalaAnnotationBuilder.FixBuilder =
    annotationBuilder.newLocalQuickFix(fix, problemDescriptor)

  override def create(): Unit =
    annotationBuilder.create()

  import scala.language.implicitConversions

  private implicit def toScalaFixBuilder(fixBuilder: AnnotationBuilder.FixBuilder): ScalaAnnotationBuilder.FixBuilder =
    new ScalaFixBuilderAdapter(fixBuilder)

  private class ScalaFixBuilderAdapter(val fixBuilder: AnnotationBuilder.FixBuilder)
    extends ScalaAnnotationBuilder.FixBuilder {

    override def range(range: TextRange): ScalaAnnotationBuilder.FixBuilder = fixBuilder.range(range)

    override def key(key: HighlightDisplayKey): ScalaAnnotationBuilder.FixBuilder = fixBuilder.key(key)

    override def batch: ScalaAnnotationBuilder.FixBuilder = fixBuilder.batch()

    override def universal: ScalaAnnotationBuilder.FixBuilder = fixBuilder.universal()

    override def registerFix: ScalaAnnotationBuilderAdapter.this.type = {
      fixBuilder.registerFix()
      ScalaAnnotationBuilderAdapter.this
    }
  }
}