package org.jetbrains.plugins.scala.annotator.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.extensions._
import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.codeInsight.template.{TemplateEditingAdapter, TemplateManager, Template, TemplateBuilderImpl}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression

/**
 * Pavel Fatin
 */

class CreateParameterlessMethodQuickFix(ref: ScReferenceExpression) extends IntentionAction {
  def getText: String = "Create parameterless method '%s'".format(ref.getText)

  def getFamilyName = getText

  def isAvailable(project: Project, editor: Editor, file: PsiFile) = true

  def startInWriteAction: Boolean = true

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace()

    val methodType = ref.expectedType.map(_.presentableText).getOrElse("Any")

    inWriteAction {
      val parents = ref.parents.toList
      val anchors = (ref :: parents)

      val place = parents.zip(anchors).find {
        case (_ : ScTemplateBody, _) => true
        case (_ : ScalaFile, _) => true
        case _ => false
      }

      val (holder, anchor) = place.get

      val text = "def %s: Int = {}".format(ref.getText)
      val method = holder.addAfter(createMethodFromText(text, ref.getManager), anchor)
      holder.addBefore(createNewLine(ref.getManager, "\n\n"), method)
      holder.addAfter(createNewLine(ref.getManager), method)

      val builder = new TemplateBuilderImpl(method)
      val typeElement = method.children.findByType(classOf[ScSimpleTypeElement]).get
      builder.replaceElement(typeElement, methodType)

      builder.setEndVariableAfter(method.getLastChild.getFirstChild)

      CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(method)

      val template = builder.buildTemplate()

      val newEditor = positionCursor(project, file, method.getLastChild)

      val range = method.getTextRange
      newEditor.getDocument.deleteString(range.getStartOffset, range.getEndOffset)

      TemplateManager.getInstance(project).startTemplate(editor, template, new TemplateEditingAdapter() {
        override def templateFinished(template: Template, brokenOff: Boolean) {
          PsiDocumentManager.getInstance(project).commitDocument(newEditor.getDocument)
          val offset = newEditor.getCaretModel.getOffset
          val element = PsiTreeUtil.findElementOfClassAtOffset(file, offset, classOf[ScFunction], false)
          Option(element).foreach {
            CodeStyleManager.getInstance(project).reformat(_)
          }
        }
      })
    }
  }

  protected def positionCursor(project: Project, targetFile: PsiFile, element: PsiElement): Editor = {
      val range = element.getTextRange
      val textOffset = range.getStartOffset
      val descriptor = new OpenFileDescriptor(project, targetFile.getVirtualFile, textOffset)
      FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
    }
  }