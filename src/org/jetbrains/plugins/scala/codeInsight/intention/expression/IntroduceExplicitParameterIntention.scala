package org.jetbrains.plugins.scala
package codeInsight.intention.expression

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.template._
import com.intellij.codeInsight.template.impl.{TemplateManagerImpl, TemplateState}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.{EditorColors, EditorColorsManager}
import com.intellij.openapi.editor.markup.{RangeHighlighter, TextAttributes}
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import com.intellij.refactoring.rename.inplace.MyLookupExpression
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaVariableValidator
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * @author Ksenia.Sautina
 * @since 4/13/12
 */

object IntroduceExplicitParameterIntention {
  def familyName = "Introduce explicit parameter"
}

class IntroduceExplicitParameterIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = IntroduceExplicitParameterIntention.familyName

  override def getText: String = getFamilyName

  def isAvailable(project: Project, editor: Editor, _element: PsiElement): Boolean = {
    findExpression(_element, editor) match {
      case Some(x) => true
      case None => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val expr = findExpression(element, editor).get
    if (expr == null || !expr.isValid) return

    val buf = new StringBuilder
    val underscores = ScUnderScoreSectionUtil.underscores(expr)
    val parentStartOffset =
      if (ApplicationManager.getApplication.isUnitTestMode) underscores(0).getTextRange.getStartOffset
      else expr.getTextRange.getStartOffset
    val parentEndOffset =
      if (ApplicationManager.getApplication.isUnitTestMode) {
        if (underscores.nonEmpty) underscores(underscores.size - 1).getTextRange.getEndOffset
        else underscores(0).getTextRange.getEndOffset
      } else expr.getTextRange.getEndOffset

    val underscoreToParam: mutable.HashMap[ScUnderscoreSection, ScParameter] =
      new mutable.HashMap[ScUnderscoreSection, ScParameter]
    val offsets: mutable.HashMap[String, Int] = new mutable.HashMap[String, Int]
    val usedNames: mutable.HashSet[String] = new mutable.HashSet[String]
    val macros: mutable.HashSet[String] = new mutable.HashSet[String]
    var needComma = false
    var needBraces = false

    for (m <- Extensions.getExtensions(Macro.EP_NAME)) {
      macros.add(m.getName)
    }

    for (u <- underscores) {
      if (needComma) buf.append(",")
      if (underscores.size > 1) needComma = true

      val names = NameSuggester.suggestNames(u,
        new ScalaVariableValidator(null, project, u, false, expr.getContext, expr.getContext) {
          override def validateName(name: String, increaseNumber: Boolean): String = {
            var res = super.validateName(name, increaseNumber)
            var index = 1

            if (usedNames.contains(res)) {
              val indexStr = res.replaceAll(name, "")
              if (indexStr != "") index = Integer.valueOf(indexStr)

              while (usedNames.contains(name + index)) {
                index = index + 1
              }
            } else {
              return res
            }
            res = name + index
            res
          }
        })

      var un = names(0)
      if (macros.contains(un)) {
        if (names.length > 1) {
          un = names(1)
        } else {
          un = "value"
        }
      }

      usedNames.add(un)
      buf.append(un)

      u.getParent match {
        case typedStmt: ScTypedStmt =>
          needBraces = true
          implicit val typeSystem = project.typeSystem
          buf.append(": ").append(typedStmt.getType(TypingContext.empty).get.canonicalText)
        case _ =>
      }

      val newParam = ScalaPsiElementFactory.createParameterFromText(un, element.getManager)
      underscoreToParam.put(u, newParam)
    }

    inWriteAction {
      for (u <- underscores) {
        val param = underscoreToParam.get(u)
        val replaced = u.replace(param.get).asInstanceOf[ScParameter]
        underscoreToParam.put(u, replaced)
        offsets.put(param.get.name, replaced.getTextRange.getStartOffset)
      }
    }

    if (underscores.size > 1 || needBraces) buf.insert(0, "(").append(")")
    val arrow = ScalaPsiUtil.functionArrow(project)
    buf.append(s" $arrow ")
    val diff = buf.length
    buf.append(expr.getText)

    val newExpr = ScalaPsiElementFactory.createExpressionFromText(buf.toString(), element.getManager)

    inWriteAction {
      val document = editor.getDocument

      expr.replace(newExpr)
      PsiDocumentManager.getInstance(project).commitDocument(document)

      val file = PsiDocumentManager.getInstance(project).getPsiFile(document)
      val parent = PsiTreeUtil.findCommonParent(file.findElementAt(parentStartOffset), file.findElementAt(parentEndOffset - 1))

      val builder: TemplateBuilderImpl = TemplateBuilderFactory.getInstance().
              createTemplateBuilder(parent).asInstanceOf[TemplateBuilderImpl]
      val params = new mutable.HashMap[Int, String]()
      val depends = new mutable.HashMap[Int, String]()

      var index: Int = 1
      parent match {
        case f: ScFunctionExpr =>
          for (parameter <- f.parameters) {
            val lookupExpr = new MyLookupExpression(parameter.name, null, parameter, f, false, null)
            builder.replaceElement(parameter.nameId, parameter.name, lookupExpr, true)

            val dependantParam = file.findElementAt(offsets(parameter.name) + diff)
            builder.replaceElement(dependantParam, parameter.name + "_1", parameter.name, false)

            params.put(index, parameter.name)
            depends.put(index, parameter.name + "_1")
            index = index + 1
          }

        case _ =>
      }

      CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(parent)

      editor.getCaretModel.moveToOffset(parent.getTextRange.getStartOffset)
      val template = builder.buildInlineTemplate()
      val myHighlighters = new ArrayBuffer[RangeHighlighter]
      val rangesToHighlight: mutable.HashMap[TextRange, TextAttributes] = new mutable.HashMap[TextRange, TextAttributes]

      TemplateManager.getInstance(project).startTemplate(editor, template, new TemplateEditingAdapter {
        override def waitingForInput(template: Template) {
          markCurrentVariables(1)
        }

        override def currentVariableChanged(templateState: TemplateState, template: Template,
                                            oldIndex: Int, newIndex: Int) {
          if (oldIndex >= 0) clearHighlighters()
          if (newIndex > 0) markCurrentVariables(newIndex + 1)
        }

        override def templateCancelled(template: Template) {
          clearHighlighters()
        }

        override def templateFinished(template: Template, brokenOff: Boolean) {
          clearHighlighters()
        }

        private def addHighlights(ranges: mutable.HashMap[TextRange, TextAttributes], editor: Editor,
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

        private def markCurrentVariables(index: Int) {
          val colorsManager: EditorColorsManager = EditorColorsManager.getInstance
          val templateState: TemplateState = TemplateManagerImpl.getTemplateState(editor)
          var i: Int = 0

          while (i < templateState.getSegmentsCount) {
            val segmentOffset: TextRange = templateState.getSegmentRange(i)
            val name: String = template.getSegmentName(i)
            var attributes: TextAttributes = null
            if (name == params.get(index).get) {
              attributes = colorsManager.getGlobalScheme.getAttributes(EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES)
            }
            else if (name == depends.get(index).get) {
              attributes = colorsManager.getGlobalScheme.getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES)
            }
            if (attributes != null) rangesToHighlight.put(segmentOffset, attributes)
            i += 1
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

      PsiDocumentManager.getInstance(project).commitDocument(document)
    }
  }

  private def findExpression(_element: PsiElement, editor: Editor): Option[ScExpression] = {
    var element: PsiElement = _element
    if (!element.getParent.isInstanceOf[ScUnderscoreSection]) {
      if (element.getTextRange.getStartOffset == editor.getCaretModel.getOffset) {
        val offset = element.getTextRange.getStartOffset - 1
        if (offset < 0) return None
        element = element.getContainingFile.findElementAt(offset)
      }
    }

    while (element != null) {
      element match {
        case expression: ScExpression =>
          if (ScUnderScoreSectionUtil.isUnderscoreFunction(element)) {
            val underscores = ScUnderScoreSectionUtil.underscores(element)
            val offset = editor.getCaretModel.getOffset
            for (u <- underscores) {
              val range: TextRange = u.getTextRange
              if (range.getStartOffset <= offset && offset <= range.getEndOffset) return Some(expression)
            }
            return None
          }
        case _ =>
      }
      element = element.getParent
    }

    None
  }
}
