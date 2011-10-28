package org.jetbrains.plugins.scala.annotator.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.extensions._
import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.codeInsight.template.{TemplateManager, TemplateBuilderImpl}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.lang.psi.types.Any
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScMember}

/**
 * Pavel Fatin
 */

class CreateApplyQuickFix(ref: ScStableCodeReferenceElement, call: ScMethodCall) extends IntentionAction {
  def getText = "Create 'apply' method"

  def getFamilyName = getText

  def isAvailable(project: Project, editor: Editor, file: PsiFile) = true

  def startInWriteAction = true

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    if (!ref.isValid) return

    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace()

    val methodType = call.expectedType().map(_.presentableText)
    val parameters = parametersFor(ref)

    inWriteAction {
      val placeholder = if (methodType.isDefined) "def apply%s: Int = " else "def apply%s = "
      val text = placeholder.format(parameters.mkString)

      val entity = createEntity(blockFor(ref).get, ref, text)

      ScalaPsiUtil.adjustTypes(entity)

      val builder = new TemplateBuilderImpl(entity)

      for (aType <- methodType;
           typeElement <- entity.children.findByType(classOf[ScSimpleTypeElement])) {
        builder.replaceElement(typeElement, aType)
      }

      entity.depthFirst.filterByType(classOf[ScParameter]).foreach { parameter =>
        val id = parameter.getNameIdentifier
        builder.replaceElement(id, id.getText)

        parameter.paramType.foreach { it =>
          builder.replaceElement(it, it.getText)
        }
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

  private def blockFor(exp: ScStableCodeReferenceElement) = {
    Option(exp.resolve()).flatMap(_.asOptionOf[ScTypeDefinition]).map(_.extendsBlock)
  }

  def createEntity(block: ScExtendsBlock, ref: ScStableCodeReferenceElement, text: String): PsiElement = {
    if (block.templateBody.isEmpty)
      block.add(createTemplateBody(block.getManager))

    val anchor = block.templateBody.get.getFirstChild
    val holder = anchor.getParent
    val hasMembers = holder.children.findByType(classOf[ScMember]).isDefined

    val entity = holder.addAfter(parseElement(text, ref.getManager), anchor)

    if (hasMembers) holder.addAfter(createNewLine(ref.getManager), entity)

    entity
  }

  private def parametersFor(ref: ScStableCodeReferenceElement): String = {
      val types = call.argumentExpressions.map(_.getType(TypingContext.empty).getOrAny)
      val names = types.map(NameSuggester.suggestNamesByType(_).headOption.getOrElse("value"))
      val uniqueNames = names.foldLeft(List[String]()) { (r, h) =>
        (h #:: Stream.from(1).map(h + _)).find(!r.contains(_)).get :: r
      }
      (uniqueNames.reverse, types).zipped.map((name, tpe) => "%s: %s".format(name, tpe.canonicalText)).mkString("(", ", ", ")")
  }

  private def positionCursor(project: Project, targetFile: PsiFile, element: PsiElement): Editor = {
    val range = element.getTextRange
    val textOffset = range.getStartOffset
    val descriptor = new OpenFileDescriptor(project, targetFile.getVirtualFile, textOffset)
    FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
  }
}