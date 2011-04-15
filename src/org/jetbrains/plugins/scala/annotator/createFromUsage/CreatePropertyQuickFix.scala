package org.jetbrains.plugins.scala.annotator.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.extensions._
import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.codeInsight.template.{TemplateManager, TemplateBuilderImpl}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}

/**
 * Pavel Fatin
 */

abstract class CreatePropertyQuickFix(ref: ScReferenceExpression,
                                      entity: String, keyword: String) extends IntentionAction {
  // TODO add private modifiers for unqualified entities ?
  // TODO create {} body if needed

  def getText = "Create %s '%s'".format(entity, ref.getText)

  def getFamilyName = getText

  def isAvailable(project: Project, editor: Editor, file: PsiFile) = true

  def startInWriteAction: Boolean = true

  private def anchorForQualified(ref: ScReferenceExpression): Option[PsiElement] = {
    ref.qualifier collect {
      case PsiReferenceEx.resolve(
             ScTemplateDefinition.extendsBlock(
               ScExtendsBlock.templateBody(body))) => body.getFirstChild
    }
  }

  private def anchorForUnqualified(ref: ScReferenceExpression): Option[PsiElement] = {
    val parents = ref.parents.toList
    val anchors = (ref :: parents)

    val place = parents.zip(anchors).find {
      case (_ : ScTemplateBody, _) => true
      case (_ : ScalaFile, _) => true
      case _ => false
    }

    place.map(_._2)
  }

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace()

    val entityType = ref.expectedType.map(_.presentableText)

    inWriteAction {
      val place = if (ref.qualifier.isDefined) anchorForQualified(ref) else anchorForUnqualified(ref)

      for (anchor <- place; holder <- anchor.parent) {
        val placeholder = if (entityType.isDefined) "%s %s: Int" else "%s %s"
        val text = placeholder.format(keyword, ref.nameId.getText)

        val entity = holder.addAfter(parseElement(text, ref.getManager), anchor)

        if (ref.qualifier.isEmpty)
          holder.addBefore(createNewLine(ref.getManager, "\n\n"), entity)

        val builder = new TemplateBuilderImpl(entity)

        for (aType <- entityType;
             typeElement <- entity.children.findByType(classOf[ScSimpleTypeElement])) {
          builder.replaceElement(typeElement, aType)
        }

        CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(entity)

        val template = builder.buildTemplate()

        val targetFile = entity.getContainingFile
        val newEditor = positionCursor(project, targetFile, entity.getLastChild)
        val range = entity.getTextRange
        newEditor.getDocument.deleteString(range.getStartOffset, range.getEndOffset)
        TemplateManager.getInstance(project).startTemplate(newEditor, template)
      }
    }
  }

  protected def positionCursor(project: Project, targetFile: PsiFile, element: PsiElement): Editor = {
      val range = element.getTextRange
      val textOffset = range.getStartOffset
      val descriptor = new OpenFileDescriptor(project, targetFile.getVirtualFile, textOffset)
      FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
    }
  }