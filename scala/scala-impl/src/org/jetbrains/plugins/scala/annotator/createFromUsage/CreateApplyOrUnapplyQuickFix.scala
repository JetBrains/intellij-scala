package org.jetbrains.plugins.scala
package annotator.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.template.{TemplateBuilder, TemplateBuilderImpl, TemplateManager}
import com.intellij.codeInsight.{CodeInsightUtilCore, FileModificationService}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.TypeAnnotationUtil

abstract class CreateApplyOrUnapplyQuickFix(td: ScTypeDefinition)
        extends IntentionAction {
  private implicit val ctx: ProjectContext = td.projectContext

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = {
    if (!td.isValid) return false
    td.getContainingFile match {
      case _: ScalaCodeFragment => false
      case f: ScalaFile if f.isWritable => true
      case _ => false
    }
  }

  override def startInWriteAction: Boolean = false

  protected def createEntity(block: ScExtendsBlock, text: String): PsiElement = {
    if (block.templateBody.isEmpty)
      block.add(createTemplateBody(block.getManager))

    val anchor = block.templateBody.get.getFirstChild
    val holder = anchor.getParent
    val hasMembers = holder.children.containsInstanceOf[ScMember]

    val entity = holder.addAfter(createElementFromText(text), anchor)
    if (hasMembers) holder.addAfter(createNewLine(), entity)

    entity
  }

  protected def methodType: Option[String]

  protected def methodText: String

  protected def addElementsToTemplate(method: ScFunction, builder: TemplateBuilder): Unit

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    if (!FileModificationService.getInstance.prepareFileForWrite(file)) return

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace()

    inWriteAction {
      val entity = createEntity(td.extendsBlock, methodText).asInstanceOf[ScFunction]

      ScalaPsiUtil.adjustTypes(entity)
      entity match {
        case scalaPsi: ScalaPsiElement => TypeAnnotationUtil.removeTypeAnnotationIfNeeded(scalaPsi)
        case _ =>
      }

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
