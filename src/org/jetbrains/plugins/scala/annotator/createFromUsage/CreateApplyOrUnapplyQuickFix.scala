package org.jetbrains.plugins.scala
package annotator.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.template.{TemplateBuilder, TemplateBuilderImpl, TemplateManager}
import com.intellij.codeInsight.{CodeInsightUtilCore, FileModificationService}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile}
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaCodeFragment
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._

/**
 * Nikolay.Tropin
 * 2014-08-01
 */
abstract class CreateApplyOrUnapplyQuickFix(td: ScTypeDefinition)
        extends IntentionAction {
  override val getText = {
    val classKind = td match {
      case _: ScObject => "object"
      case _: ScTrait => "trait"
      case _: ScClass => "class"
      case _ => ""
    }
    s"$getFamilyName in $classKind ${td.name}"
  }

  def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = {
    if (!td.isValid) return false
    td.getContainingFile match {
      case _: ScalaCodeFragment => false
      case f: ScalaFile if f.isWritable => true
      case _ => false
    }
  }

  def startInWriteAction = false

  protected def createEntity(block: ScExtendsBlock, text: String): PsiElement = {
    if (block.templateBody.isEmpty)
      block.add(createTemplateBody(block.getManager))

    val anchor = block.templateBody.get.getFirstChild
    val holder = anchor.getParent
    val hasMembers = holder.children.findByType(classOf[ScMember]).isDefined

    val entity = holder.addAfter(parseElement(text, td.getManager), anchor)

    if (hasMembers) holder.addAfter(createNewLine(td.getManager), entity)

    entity
  }

  protected def methodType: Option[String]

  protected def methodText: String

  protected def addElementsToTemplate(method: ScFunction, builder: TemplateBuilder): Unit

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    if (!FileModificationService.getInstance.prepareFileForWrite(file)) return

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace()

    inWriteAction {
      val entity = createEntity(td.extendsBlock, methodText).asInstanceOf[ScFunction]

      ScalaPsiUtil.adjustTypes(entity)

      val builder = new TemplateBuilderImpl(entity)

      addElementsToTemplate(entity, builder)

      CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(entity)

      val template = builder.buildTemplate()

      val newEditor = CreateFromUsageUtil.positionCursor(entity.getLastChild)
      val range = entity.getTextRange
      newEditor.getDocument.deleteString(range.getStartOffset, range.getEndOffset)
      TemplateManager.getInstance(project).startTemplate(newEditor, template)
    }
  }

}
