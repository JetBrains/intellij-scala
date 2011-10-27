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
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, Any}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
 * Pavel Fatin
 */

abstract class CreateEntityQuickFix(ref: ScReferenceExpression,
                                      entity: String, keyword: String) extends IntentionAction {
  // TODO add private modifiers for unqualified entities ?
  // TODO use Java CFU when needed
  // TODO find better place for fields, create methods after

  def getText = "Create %s '%s'".format(entity, ref.nameId.getText)

  def getFamilyName = getText

  def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = {
    if (!ref.isValid) return false
    ref match {
      case Both(Parent(_: ScAssignStmt), Parent(Parent(_: ScArgumentExprList))) =>
        false
      case exp @ Parent(infix: ScInfixExpr) if infix.operation == exp =>
        blockFor(infix.getBaseExpr).exists(!_.isInCompiledFile)
      case it =>
        if (it.isQualified)
          ref.qualifier.flatMap(blockFor).exists(!_.isInCompiledFile)
        else
          !it.isInCompiledFile
    }
  }

  def startInWriteAction: Boolean = true

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    if (!ref.isValid) return

    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace()

    val entityType = typeFor(ref)
    val parameters = parametersFor(ref)

    inWriteAction {
      val placeholder = if (entityType.isDefined) "%s %s%s: Int" else "%s %s%s"
      val text = placeholder.format(keyword, ref.nameId.getText, parameters.mkString)

      val entity = ref match {
        case it if it.isQualified =>
          createEntity(ref.qualifier.flatMap(blockFor).get, ref, text)
        case Parent(infix: ScInfixExpr) =>
          createEntity(blockFor(infix.getBaseExpr).get, ref, text)
        case _ =>
          createEntity(ref, text)
      }

      ScalaPsiUtil.adjustTypes(entity)

      val builder = new TemplateBuilderImpl(entity)

      for (aType <- entityType;
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

  private def blockFor(exp: ScExpression) = Some(exp).collect {
    case ScExpression.Type(ScType.ExtractClass(ScTemplateDefinition.ExtendsBlock(block))) => block
  }

  def createEntity(block: ScExtendsBlock, ref: ScReferenceExpression, text: String): PsiElement = {
    if (block.templateBody.isEmpty)
      block.add(createTemplateBody(block.getManager))

    val anchor = block.templateBody.get.getFirstChild
    val holder = anchor.getParent
    val hasMembers = holder.children.findByType(classOf[ScMember]).isDefined

    val entity = holder.addAfter(parseElement(text, ref.getManager), anchor)

    if (hasMembers) holder.addAfter(createNewLine(ref.getManager), entity)

    entity
  }

  def createEntity(ref: ScReferenceExpression, text: String): PsiElement = {
    val anchor = anchorForUnqualified(ref).get
    val holder = anchor.getParent

    val entity = holder.addBefore(parseElement(text, ref.getManager), anchor)

    holder.addBefore(createNewLine(ref.getManager, "\n\n"), entity)
    holder.addAfter(createNewLine(ref.getManager, "\n\n"), entity)

    entity
  }

  private def typeFor(ref: ScReferenceExpression): Option[String]  = ref.getParent match {
    case call: ScMethodCall => call.expectedType.map(_.presentableText)
    case _ => ref.expectedType.map(_.presentableText)
  }

  private def parametersFor(ref: ScReferenceExpression): Option[String] = ref.parent.collect {
    case call: MethodInvocation =>
      val types = call.argumentExpressions.map(_.getType(TypingContext.empty).getOrAny)
      val names = types.map(NameSuggester.suggestNamesByType(_).headOption.getOrElse("value"))
      val uniqueNames = names.foldLeft(List[String]()) { (r, h) =>
        (h #:: Stream.from(1).map(h + _)).find(!r.contains(_)).get :: r
      }
      (uniqueNames.reverse, types).zipped.map((name, tpe) => "%s: %s".format(name, tpe.canonicalText)).mkString("(", ", ", ")")
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

  private def positionCursor(project: Project, targetFile: PsiFile, element: PsiElement): Editor = {
    val range = element.getTextRange
    val textOffset = range.getStartOffset
    val descriptor = new OpenFileDescriptor(project, targetFile.getVirtualFile, textOffset)
    FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
  }
}