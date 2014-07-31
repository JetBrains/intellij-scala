package org.jetbrains.plugins.scala.annotator.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.template.{TemplateBuilderImpl, TemplateManager}
import com.intellij.codeInsight.{CodeInsightUtilCore, FileModificationService}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.annotator.createFromUsage.CreateEntityQuickFix._
import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.config.ScalaVersionUtil
import org.jetbrains.plugins.scala.console.ScalaLanguageConsoleView
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaCodeFragment
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSelfTypeElement, ScSimpleTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScTypeParam, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester

/**
 * Pavel Fatin
 */

abstract class CreateEntityQuickFix(ref: ScReferenceExpression, entity: String, keyword: String)
        extends CreateFromUsageQuickFixBase(ref, entity) {
  // TODO add private modifiers for unqualified entities ?
  // TODO use Java CFU when needed
  // TODO find better place for fields, create methods after

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = {
    if (!super.isAvailable(project, editor, file)) return false

    ref match {
      case Both(Parent(_: ScAssignStmt), Parent(Parent(_: ScArgumentExprList))) =>
        false
      case exp@Parent(infix: ScInfixExpr) if infix.operation == exp =>
        blockFor(infix.getBaseExpr).exists(!_.isInCompiledFile)
      case it =>
        it.qualifier match {
          case Some(sup: ScSuperReference) => unambiguousSuper(sup).exists(!_.isInCompiledFile)
          case Some(qual) => blockFor(qual).exists(!_.isInCompiledFile)
          case None => !it.isInCompiledFile
        }
    }
  }

  def invokeInner(project: Project, editor: Editor, file: PsiFile) {
    val entityType = typeFor(ref)
    val genericParams = genericParametersFor(ref)
    val parameters = parametersFor(ref)
    val Q_MARKS = "???"

    val placeholder = if (entityType.isDefined) "%s %s%s: Int" else "%s %s%s"
    import org.jetbrains.plugins.scala.config.ScalaVersionUtil._
    val unimplementedBody = if (isGeneric(file, false, SCALA_2_10, SCALA_2_11)) " = ???" else ""
    val params = (genericParams ++: parameters).mkString
    val text = placeholder.format(keyword, ref.nameId.getText, params) + unimplementedBody

    val block = ref match {
      case it if it.isQualified => ref.qualifier.flatMap(blockFor)
      case Parent(infix: ScInfixExpr) => blockFor(infix.getBaseExpr)
      case _ => None
    }

    if (!FileModificationService.getInstance.prepareFileForWrite(block.map(_.getContainingFile).getOrElse(file))) return

    inWriteAction {
      val entity = block match {
        case Some(_ childOf (obj: ScObject)) if obj.isSyntheticObject =>
          val bl = materializeSytheticObject(obj).extendsBlock
          createEntity(bl, ref, text)
        case Some(it) => createEntity(it, ref, text)
        case None => createEntity(ref, text)
      }

      ScalaPsiUtil.adjustTypes(entity)

      val builder = new TemplateBuilderImpl(entity)

      for (aType <- entityType;
           typeElement <- entity.children.findByType(classOf[ScSimpleTypeElement])) {
        builder.replaceElement(typeElement, aType)
      }

      CreateFromUsageUtil.addTypeParametersToTemplate(entity, builder)
      CreateFromUsageUtil.addParametersToTemplate(entity, builder)

      entity.lastChild.foreach { case qmarks: ScReferenceExpression if qmarks.getText == Q_MARKS => builder.replaceElement(qmarks, Q_MARKS)}

      CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(entity)

      val template = builder.buildTemplate()

      val isScalaConsole = file.getName == ScalaLanguageConsoleView.SCALA_CONSOLE
      if (!isScalaConsole) {
        val targetFile = entity.getContainingFile
        val newEditor = positionCursor(project, targetFile, entity.getLastChild)
        val range = entity.getTextRange
        newEditor.getDocument.deleteString(range.getStartOffset, range.getEndOffset)
        TemplateManager.getInstance(project).startTemplate(newEditor, template)
      }
    }
  }
}

object CreateEntityQuickFix {
  private def materializeSytheticObject(obj: ScObject): ScObject = {
    val clazz = obj.fakeCompanionClassOrCompanionClass
    val objText = s"object ${clazz.name} {}"
    val fromText = ScalaPsiElementFactory.createTemplateDefinitionFromText(objText, clazz.getParent, clazz)
    clazz.getParent.addAfter(fromText, clazz).asInstanceOf[ScObject]
  }

  private def blockFor(exp: ScExpression) = {
    object ParentExtendsBlock {
      def unapply(e: PsiElement): Option[ScExtendsBlock] = Option(PsiTreeUtil.getParentOfType(exp, classOf[ScExtendsBlock]))
    }

    Some(exp).collect {
      case InstanceOfClass(td: ScTemplateDefinition) => td.extendsBlock
      case th: ScThisReference =>
        th.refTemplate match {
          case Some(ScTemplateDefinition.ExtendsBlock(block)) => block
          case None => PsiTreeUtil.getParentOfType(th, classOf[ScExtendsBlock], /*strict = */true, /*stopAt = */classOf[ScTemplateDefinition])
        }
      case sup: ScSuperReference =>
        unambiguousSuper(sup) match {
          case Some(ScTemplateDefinition.ExtendsBlock(block)) => block
          case None => throw new IllegalArgumentException("Cannot find template definition for not-static super reference")
        }
      case Both(th: ScThisReference, ParentExtendsBlock(block)) => block
      case Both(ReferenceTarget((_: ScSelfTypeElement)), ParentExtendsBlock(block)) => block
    }
  }

  def createEntity(block: ScExtendsBlock, ref: ScReferenceExpression, text: String): PsiElement = {
    if (block.templateBody.isEmpty)
      block.add(createTemplateBody(block.getManager))

    val children = block.templateBody.get.children.toSeq
    val anchor = children.find(_.isInstanceOf[ScSelfTypeElement]).getOrElse(children.head)
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

  private def typeFor(ref: ScReferenceExpression): Option[String] = ref.getParent match {
    case call: ScMethodCall => call.expectedType().map(_.presentableText)
    case _ => ref.expectedType().map(_.presentableText)
  }

  private def parametersFor(ref: ScReferenceExpression): Option[String] = {
    ref.parent.collect {
      case MethodRepr(_, _, Some(`ref`), args) => CreateFromUsageUtil.argsText(args)
      case (_: ScGenericCall) childOf (MethodRepr(_, _, Some(`ref`), args)) => CreateFromUsageUtil.argsText(args)
    }
  }

  private def genericParametersFor(ref: ScReferenceExpression): Option[String] = ref.parent.collect {
    case genCall: ScGenericCall => 
      genCall.arguments match {
        case args if args.size == 1 => "[T]"
        case args => args.indices.map(i => s"T$i").mkString("[", ", ", "]")
      }
      
  }

  private def anchorForUnqualified(ref: ScReferenceExpression): Option[PsiElement] = {
    val parents = ref.parents.toList
    val anchors = ref :: parents

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

  private def unambiguousSuper(supRef: ScSuperReference): Option[ScTypeDefinition] = {
    supRef.staticSuper match {
      case Some(ScType.ExtractClass(clazz: ScTypeDefinition)) => Some(clazz)
      case None =>
        supRef.parents.toSeq.collect { case td: ScTemplateDefinition => td } match {
          case Seq(td) =>
            td.supers match {
              case Seq(t: ScTypeDefinition) => Some(t)
              case _ => None
            }
          case _ => None
        }
    }
  }

}
