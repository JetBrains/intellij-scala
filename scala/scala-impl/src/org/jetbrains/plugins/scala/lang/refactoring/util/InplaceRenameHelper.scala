package org.jetbrains.plugins.scala
package lang.refactoring.util

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.codeInsight.template._
import com.intellij.codeInsight.template.impl.{TemplateImpl, TemplateManagerImpl, TemplateState, TextExpression}
import com.intellij.openapi.editor.colors.{EditorColors, EditorColorsManager}
import com.intellij.openapi.editor.markup.{RangeHighlighter, TextAttributes}
import com.intellij.openapi.editor.{Document, Editor, EditorFactory, RangeMarker}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile, PsiNamedElement}
import com.intellij.refactoring.rename.inplace.MyLookupExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

import scala.jdk.CollectionConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * Nikolay.Tropin
 * 5/12/13
 */
class InplaceRenameHelper(parent: PsiElement) {
  private val builder: TemplateBuilderImpl = TemplateBuilderFactory.getInstance().
          createTemplateBuilder(parent).asInstanceOf[TemplateBuilderImpl]
  private val primaries = mutable.ArrayBuffer[PsiElement]()
  private val primaryNames = mutable.HashMap[PsiElement, String]()
  private val dependentNames = mutable.HashMap[PsiElement, collection.Seq[String]]()
  val project: Project = parent.getProject
  val file: PsiFile = parent.getContainingFile
  val document: Document = PsiDocumentManager.getInstance(project).getDocument(file)
  val editor: Editor = EditorFactory.getInstance.getEditors(document)(0)

  def addGroup(primary: PsiElement,
               newName: String,
               dependentsWithRanges: collection.Seq[(PsiElement, TextRange)],
               suggestedNames: Iterable[String]): Unit = {
    val names = new java.util.LinkedHashSet[String]()
    suggestedNames.foreach(names.add)
    val lookupExpr = primary match {
      case named: PsiNamedElement => new MyLookupExpression(newName, names, named, parent, false, null)
      case _ => new TextExpression(newName)
    }

    builder.replaceElement(primary, newName, lookupExpr, true)

    val depNames = mutable.ArrayBuffer[String]()
    for (index <- dependentsWithRanges.indices) {
      val dependentName: String = newName + "_" + index
      depNames += dependentName
      val (depElem, depRange) = dependentsWithRanges(index)
      if (depRange != null) builder.replaceElement(depElem, depRange, dependentName, newName, false)
      else builder.replaceElement(depElem, dependentName, newName, false)
    }
    primaries += primary
    primaryNames += (primary -> newName)
    dependentNames += (primary -> depNames)
  }

  def addGroup(primary: ScNamedElement, dependents: collection.Seq[PsiElement], suggestedNames: Iterable[String]): Unit = {
    addGroup(primary.nameId, primary.name, dependents.map((_, null)), suggestedNames)
  }

  def startRenaming(): Unit = {
    CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(parent)
    editor.getCaretModel.moveToOffset(parent.getTextRange.getStartOffset)

    val template = builder.buildInlineTemplate().asInstanceOf[TemplateImpl]
    val templateVariables = template.getVariables
    val stopAtVariables = templateVariables.asScala.filter(_.isAlwaysStopAt)
    val primarySortedVariables = primaries.flatMap(p => stopAtVariables.find(_.getName == primaryNames(p)))
    for ((v, idx) <- primarySortedVariables.zipWithIndex) {
      templateVariables.set(idx, v)
    }
    val myHighlighters = mutable.ArrayBuffer[RangeHighlighter]()
    val rangesToHighlight = mutable.HashMap[RangeMarker, TextAttributes]()

    TemplateManager.getInstance(project).startTemplate(editor, template, new TemplateEditingAdapter {
      override def waitingForInput(template: Template): Unit = {
        markCurrentVariables(0)
      }

      override def currentVariableChanged(templateState: TemplateState, template: Template,
                                          oldIndex: Int, newIndex: Int): Unit = {
        if (oldIndex >= 0) clearHighlighters()
        if (newIndex >= 0) markCurrentVariables(newIndex)
      }

      override def templateCancelled(template: Template): Unit = {
        clearHighlighters()
      }

      override def templateFinished(template: Template, brokenOff: Boolean): Unit = {
        clearHighlighters()
      }

      private def addHighlights(ranges: mutable.HashMap[RangeMarker, TextAttributes], editor: Editor,
                        highlighters: ArrayBuffer[RangeHighlighter], highlightManager: HighlightManager): Unit = {
        for ((range, attributes) <- ranges) {
          highlightManager.addOccurrenceHighlight(editor, range.getStartOffset, range.getEndOffset,
            attributes, 0, highlighters.asJava, null)
        }
        for (highlighter <- highlighters) {
          highlighter.setGreedyToLeft(true)
          highlighter.setGreedyToRight(true)
        }
      }

      private def markCurrentVariables(groupIndex: Int): Unit = {
        val colorsManager: EditorColorsManager = EditorColorsManager.getInstance
        val templateState: TemplateState = TemplateManagerImpl.getTemplateState(editor)
        val document = editor.getDocument
        val primary = primaries(groupIndex)

        for (i <- 0 until templateState.getSegmentsCount) {
          val segmentRange: TextRange = templateState.getSegmentRange(i)
          val segmentMarker: RangeMarker = document.createRangeMarker(segmentRange)
          val name: String = template.getSegmentName(i)
          val attributes: TextAttributes =
            if (name == primaryNames(primary)) colorsManager.getGlobalScheme.getAttributes(EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES)
            else if (dependentNames(primary) contains name) colorsManager.getGlobalScheme.getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES)
            else null
          if (attributes != null) rangesToHighlight.put(segmentMarker, attributes)
        }
        addHighlights(rangesToHighlight, editor, myHighlighters, HighlightManager.getInstance(project))
      }

      private def clearHighlighters(): Unit = {
        val highlightManager = HighlightManager.getInstance(project)
        myHighlighters.foreach {a => highlightManager.removeSegmentHighlighter(editor, a)}
        rangesToHighlight.clear()
        myHighlighters.clear()
      }
    })
    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
  }
}

