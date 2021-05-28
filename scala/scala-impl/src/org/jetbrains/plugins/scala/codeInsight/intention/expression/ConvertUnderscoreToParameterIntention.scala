package org.jetbrains.plugins.scala
package codeInsight.intention.expression

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInsight.template._
import com.intellij.codeInsight.template.impl.{TemplateManagerImpl, TemplateState}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.{EditorColors, EditorColorsManager, TextAttributesKey}
import com.intellij.openapi.editor.markup.{RangeHighlighter, TextAttributes}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import com.intellij.refactoring.rename.inplace.MyLookupExpression
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionFromText, createParameterFromText}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaVariableValidator
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.nowarn
import scala.jdk.CollectionConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class ConvertUnderscoreToParameterIntention extends PsiElementBaseIntentionAction {
  override def getFamilyName: String = ScalaBundle.message("family.name.convert.underscore.section.to.parameter")

  override def getText: String = getFamilyName

  override def isAvailable(project: Project, editor: Editor, _element: PsiElement): Boolean = {
    findExpression(_element, editor) match {
      case Some(_) => true
      case None => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    implicit val ctx: ProjectContext = project

    val expr = findExpression(element, editor).get
    if (expr == null || !expr.isValid) return

    val buf = new StringBuilder
    val underscores = ScUnderScoreSectionUtil.underscores(expr)
    val parentStartOffset =
      if (ApplicationManager.getApplication.isUnitTestMode) underscores.head.getTextRange.getStartOffset
      else expr.getTextRange.getStartOffset
    val parentEndOffset =
      if (ApplicationManager.getApplication.isUnitTestMode) {
        if (underscores.nonEmpty) underscores.last.getTextRange.getEndOffset
        else underscores.head.getTextRange.getEndOffset
      } else expr.getTextRange.getEndOffset

    val underscoreToParam: mutable.HashMap[ScUnderscoreSection, ScParameter] =
      new mutable.HashMap[ScUnderscoreSection, ScParameter]
    val offsets: mutable.HashMap[String, Int] = new mutable.HashMap[String, Int]
    val usedNames: mutable.HashSet[String] = new mutable.HashSet[String]
    val macros: mutable.HashSet[String] = new mutable.HashSet[String]
    var needComma = false
    var needBraces = false

    for (m <- Macro.EP_NAME.getExtensionList.asScala) {
      macros.add(m.getName)
    }

    for (u <- underscores) {
      if (needComma) buf.append(",")
      if (underscores.size > 1) needComma = true

      val names = NameSuggester.suggestNames(u,
        new ScalaVariableValidator(u, false, expr.getContext, expr.getContext) {
          override def validateName(name: String): String = {
            var res = super.validateName(name)
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

      val un = names.toList match {
        case head :: _ if !macros.contains(head) => head
        case _ :: head :: _ => head
        case _ => "value"
      }

      usedNames.add(un)
      buf.append(un)

      u.getParent match {
        case typedStmt: ScTypedExpression =>
          needBraces = true
          buf.append(": ").append(typedStmt.`type`().get.canonicalText)
        case _ =>
      }

      val newParam = createParameterFromText(un)
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
    val arrow = ScalaPsiUtil.functionArrow
    buf.append(s" $arrow ")
    val diff = buf.length
    buf.append(expr.getText)

    val newExpr = createExpressionFromText(buf.toString())

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
      val rangesToHighlight: mutable.HashMap[TextRange, TextAttributesKey] = new mutable.HashMap

      TemplateManager.getInstance(project).startTemplate(editor, template, new TemplateEditingAdapter {
        override def waitingForInput(template: Template): Unit = {
          markCurrentVariables(1)
        }

        override def currentVariableChanged(templateState: TemplateState, template: Template,
                                            oldIndex: Int, newIndex: Int): Unit = {
          if (oldIndex >= 0) clearHighlighters()
          if (newIndex > 0) markCurrentVariables(newIndex + 1)
        }

        override def templateCancelled(template: Template): Unit = {
          clearHighlighters()
        }

        override def templateFinished(template: Template, brokenOff: Boolean): Unit = {
          clearHighlighters()
        }

        private def addHighlights(ranges: mutable.HashMap[TextRange, TextAttributesKey], editor: Editor,
                                  highlighters: ArrayBuffer[RangeHighlighter], highlightManager: HighlightManager): Unit = {
          for ((range, attributes) <- ranges) {
            highlightManager.addOccurrenceHighlight(
              editor, range.getStartOffset, range.getEndOffset,
              attributes, 0, highlighters.asJava)
          }
          for (highlighter <- highlighters) {
            highlighter.setGreedyToLeft(true)
            highlighter.setGreedyToRight(true)
          }
        }

        private def markCurrentVariables(index: Int): Unit = {
          val templateState: TemplateState = TemplateManagerImpl.getTemplateState(editor)
          var i: Int = 0

          while (i < templateState.getSegmentsCount) {
            val segmentOffset: TextRange = templateState.getSegmentRange(i)
            val name: String = template.getSegmentName(i)
            var attributes: TextAttributesKey = null
            if (name == params(index)) {
              attributes = EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES
            }
            else if (name == depends(index)) {
              attributes = EditorColors.SEARCH_RESULT_ATTRIBUTES
            }
            if (attributes != null) rangesToHighlight.put(segmentOffset, attributes)
            i += 1
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
          if (ScUnderScoreSectionUtil.isUnderscoreFunction(expression)) {
            val underscores = ScUnderScoreSectionUtil.underscores(expression)
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
