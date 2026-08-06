package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.intention.CommonIntentionAction
import com.intellij.codeInspection.{LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.ProblemGroup
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.{GutterIconRenderer, TextAttributes}
import com.intellij.openapi.util.{NlsContexts, TextRange}
import com.intellij.psi.PsiElement

//noinspection ScalaDocParserErrorInspection
object ScalaAnnotationBuilder {

  trait FixBuilder {
    /**
     * Specify the range for this quick fix. If not specified, the annotation range is used.
     * This is an intermediate method in the registering new quick fix pipeline.
     */
    def range(range: TextRange): ScalaAnnotationBuilder.FixBuilder

    def key(key: HighlightDisplayKey): ScalaAnnotationBuilder.FixBuilder

    /**
     * Specify that the quickfix will be available during batch mode only.
     * This is an intermediate method in the registering new quick fix pipeline.
     */
    def batch: ScalaAnnotationBuilder.FixBuilder

    /**
     * Specify that the quickfix will be available both during batch mode and on-the-fly.
     * This is an intermediate method in the registering new quick fix pipeline.
     */
    def universal: ScalaAnnotationBuilder.FixBuilder

    /**
     * Finish registration of the new quickfix associated with the annotation.
     * After calling this method you can continue constructing the annotation - e.g. register new fixes.
     * For example:
     * <pre>```
     * holder.newAnnotation(range, WARNING, "Illegal element")
     *   .newFix(myRenameFix).key(DISPLAY_KEY).registerFix()
     *   .newFix(myDeleteFix).range(deleteRange).registerFix()
     *   .create();
     * ```
     * </pre>
     */
    def registerFix: ScalaAnnotationBuilder
  }
}

//noinspection ScalaDocParserErrorInspection,ScalaDocInlinedTag
/**
 * Clone of [[com.intellij.lang.annotation.AnnotationBuilder]],
 * with an additional `setRangeTransformer` method to support annotation of desugared elements
 *
 * see [[ScalaAnnotationHolder]]
 */
trait ScalaAnnotationBuilder {
  /**
   * We need it for now to handle desugarings (see org.jetbrains.plugins.scala.annotator.annotationHolder.DelegateAnnotationHolder)
   */
  def setRangeTransformer(transformer: TextRange => TextRange): this.type

  /**
   * Specify annotation range. When not called, the current element range is used,
   * i.e. of the element your `Annotator# annotate ( PsiElement, AnnotationHolder)` method is called with.
   * The passed `range` must be inside the range of the current element being annotated. An empty range will be highlighted as
   * `endOffset = startOffset + 1`.
   * This is an intermediate method in the creating new annotation pipeline.
   */
  def range(range: TextRange): this.type

  /**
   * Specify annotation range is equal to the `element.getTextRange()`.
   * When not called, the current element range is used, i.e. of the element your `Annotator` method is called with.
   * The range of the `element` must be inside the range of the current element being annotated.
   * This is an intermediate method in the creating new annotation pipeline.
   */
  def range(element: ASTNode): this.type

  def range(element: PsiElement): this.type

  /**
   * Specify annotation should be shown after the end of line. Useful for creating warnings of the type "unterminated string literal".
   * This is an intermediate method in the creating new annotation pipeline.
   */
  def afterEndOfLine: this.type

  /**
   * Specify annotation should be shown differently - as a sticky popup at the top of the file.
   * Useful for file-wide messages like "This file is in the wrong directory".
   * This is an intermediate method in the creating new annotation pipeline.
   */
  def fileLevel: this.type

  /**
   * Specify annotation should have an icon at the gutter.
   * Useful for distinguish annotations linked to additional resources like "this is a test method. Click on the icon gutter to run".
   * This is an intermediate method in the creating new annotation pipeline.
   */
  def gutterIconRenderer(gutterIconRenderer: GutterIconRenderer): this.type

  /**
   * Specify problem group for the annotation to group corresponding inspections.
   * This is an intermediate method in the creating new annotation pipeline.
   */
  def problemGroup(problemGroup: ProblemGroup): this.type

  /**
   * Override text attributes for the annotation to change the defaults specified for the given severity.
   * This is an intermediate method in the creating new annotation pipeline.
   */
  def enforcedTextAttributes(enforcedAttributes: TextAttributes): this.type

  /**
   * Specify text attributes for the annotation to change the defaults specified for the given severity.
   * This is an intermediate method in the creating new annotation pipeline.
   */
  def textAttributes(enforcedAttributes: TextAttributesKey): this.type

  /**
   * Specify the problem highlight type for the annotation. If not specified, the default type for the severity is used.
   * This is an intermediate method in the creating new annotation pipeline.
   */
  def highlightType(highlightType: ProblemHighlightType): this.type

  /**
   * Specify tooltip for the annotation to popup on mouse hover.
   * This is an intermediate method in the creating new annotation pipeline.
   */
  def tooltip(@NlsContexts.Tooltip tooltip: String): this.type

  /**
   * Optimization method specifying whether the annotation should be re-calculated when the user types in it.
   * This is an intermediate method in the creating new annotation pipeline.
   */
  def needsUpdateOnTyping: this.type

  /**
   * Optimization method which explicitly specifies whether the annotation should be re-calculated when the user types in it.
   * This is an intermediate method in the creating new annotation pipeline.
   */
  def needsUpdateOnTyping(value: Boolean): this.type

  /**
   * Registers quick fix for this annotation.
   * If you want to tweak the fix, e.g. modify its range, please use {@link newFix ( IntentionAction )} instead.
   * This is an intermediate method in the creating new annotation pipeline.
   */
  def withFix(fix: CommonIntentionAction): this.type

  /**
   * Begin registration of the new quickfix associated with the annotation.
   * A typical code looks like this: <p>{@code holder.newFix(action).range(fixRange).registerFix()}</p>
   *
   * @param fix an intention action to be shown for the annotation as a quick fix
   */
  def newFix(fix: CommonIntentionAction): ScalaAnnotationBuilder.FixBuilder

  /**
   * Begin registration of the new quickfix associated with the annotation.
   * A typical code looks like this: <p>{@code holder.newLocalQuickFix(fix).range(fixRange).registerFix()}</p>
   *
   * @param fix               to be shown for the annotation as a quick fix
   * @param problemDescriptor to be passed to {@link LocalQuickFix# applyFix ( Project, CommonProblemDescriptor)}
   */
  def newLocalQuickFix(fix: LocalQuickFix, problemDescriptor: ProblemDescriptor): ScalaAnnotationBuilder.FixBuilder

  /**
   * Finish creating new annotation.
   * Calling this method means you've completed your annotation, and it is ready to be shown on screen.
   */
  def create(): Unit
}