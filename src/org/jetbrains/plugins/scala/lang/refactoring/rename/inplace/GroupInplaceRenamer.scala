package org.jetbrains.plugins.scala
package lang.refactoring.rename.inplace

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.codeInsight.template._
import com.intellij.codeInsight.template.impl.{TemplateManagerImpl, TemplateState}
import com.intellij.openapi.editor.colors.{EditorColors, EditorColorsManager}
import com.intellij.openapi.editor.markup.{RangeHighlighter, TextAttributes}
import com.intellij.openapi.editor.{Document, Editor, EditorFactory, RangeMarker}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import com.intellij.refactoring.rename.inplace.MyLookupExpression
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * Nikolay.Tropin
 * 5/12/13
 */
class GroupInplaceRenamer(parent: PsiElement) {
  private val builder: TemplateBuilderImpl = TemplateBuilderFactory.getInstance().
          createTemplateBuilder(parent).asInstanceOf[TemplateBuilderImpl]
  private val primaries = mutable.ArrayBuffer[ScNamedElement]()
  private val dependentNames = mutable.HashMap[ScNamedElement, Seq[String]]()
  val project = parent.getProject
  val file = parent.getContainingFile
  val document: Document = PsiDocumentManager.getInstance(project).getDocument(file)
  val editor = EditorFactory.getInstance.getEditors(document)(0)

  def addGroup(primary: ScNamedElement, dependents: List[ScalaPsiElement], suggestedNames: Seq[String]) {
    val names = new java.util.LinkedHashSet[String]()
    suggestedNames.foreach(names.add(_))
    val lookupExpr = new MyLookupExpression(primary.name, names, primary, parent, false, null)
    builder.replaceElement(primary.nameId, primary.name, lookupExpr, true)

    val depNames = mutable.ArrayBuffer[String]()
    for (index <- 0 until dependents.size) {
      val dependentName: String = primary.name + "_" + index
      depNames += dependentName
      builder.replaceElement(dependents(index), dependentName, primary.name, false)
    }
    primaries += primary
    dependentNames += (primary -> depNames)
  }

  def startRenaming() {
    CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(parent)
    editor.getCaretModel.moveToOffset(parent.getTextRange.getStartOffset)

    val template = builder.buildInlineTemplate()
    val myHighlighters = mutable.ArrayBuffer[RangeHighlighter]()
    val rangesToHighlight = mutable.HashMap[RangeMarker, TextAttributes]()

    TemplateManager.getInstance(project).startTemplate(editor, template, new TemplateEditingAdapter {
      override def waitingForInput(template: Template) {
        markCurrentVariables(0)
      }

      override def currentVariableChanged(templateState: TemplateState, template: Template,
                                          oldIndex: Int, newIndex: Int) {
        if (oldIndex >= 0) clearHighlighters()
        if (newIndex >= 0) markCurrentVariables(newIndex)
      }

      override def templateCancelled(template: Template) {
        clearHighlighters()
      }

      override def templateFinished(template: Template, brokenOff: Boolean) {
        clearHighlighters()
      }

      private def addHighlights(ranges: mutable.HashMap[RangeMarker, TextAttributes], editor: Editor,
                        highlighters: ArrayBuffer[RangeHighlighter], highlightManager: HighlightManager) {
        for ((range, attributes) <- ranges) {
          import scala.collection.JavaConversions._
          highlightManager.addOccurrenceHighlight(editor, range.getStartOffset, range.getEndOffset,
            attributes, 0, highlighters, null)
        }
        for (highlighter <- highlighters) {
          highlighter.setGreedyToLeft(true)
          highlighter.setGreedyToRight(true)
        }
      }

      private def markCurrentVariables(groupIndex: Int) {
        val colorsManager: EditorColorsManager = EditorColorsManager.getInstance
        val templateState: TemplateState = TemplateManagerImpl.getTemplateState(editor)
        val document = editor.getDocument
        val primary = primaries(groupIndex)

        for (i <- 0 until templateState.getSegmentsCount) {
          val segmentRange: TextRange = templateState.getSegmentRange(i)
          val segmentMarker: RangeMarker = document.createRangeMarker(segmentRange)
          val name: String = template.getSegmentName(i)
          var attributes: TextAttributes = null
          if (name == primaries(groupIndex).name) {
            attributes = colorsManager.getGlobalScheme.getAttributes(EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES)
          }
          else if (dependentNames(primary) contains name) {
            attributes = colorsManager.getGlobalScheme.getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES)
          }
          if (attributes != null) rangesToHighlight.put(segmentMarker, attributes)
        }
        addHighlights(rangesToHighlight, editor, myHighlighters, HighlightManager.getInstance(project))
      }

      private def clearHighlighters() {
        val highlightManager = HighlightManager.getInstance(project)
        myHighlighters.foreach {a => highlightManager.removeSegmentHighlighter(editor, a)}
        rangesToHighlight.clear()
        myHighlighters.clear()
      }
    })
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
  }
}

