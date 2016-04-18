package org.jetbrains.plugins.scala.annotator.createFromUsage

import com.intellij.codeInsight.template.{TemplateBuilderImpl, TemplateManager}
import com.intellij.codeInsight.{CodeInsightUtilCore, FileModificationService}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.plugins.scala.annotator.createFromUsage.CreateEntityQuickFix._
import org.jetbrains.plugins.scala.annotator.createFromUsage.CreateFromUsageUtil._
import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.console.ScalaLanguageConsoleView
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSelfTypeElement, ScSimpleTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.api.{ExtractClass, TypeSystem}
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_10
import org.jetbrains.plugins.scala.project._

import scala.util.{Failure, Success, Try}

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

    implicit val typeSystem = project.typeSystem
    def checkBlock(expr: ScExpression) = blockFor(expr) match {
      case Success(bl) => !bl.isInCompiledFile
      case _ => false
    }

    ref match {
      case Both(Parent(_: ScAssignStmt), Parent(Parent(_: ScArgumentExprList))) =>
        false
      case exp@Parent(infix: ScInfixExpr) if infix.operation == exp => checkBlock(infix.getBaseExpr)
      case it =>
        it.qualifier match {
          case Some(sup: ScSuperReference) => unambiguousSuper(sup).exists(!_.isInCompiledFile)
          case Some(qual) => checkBlock(qual)
          case None => !it.isInCompiledFile
        }
    }
  }

  def invokeInner(project: Project, editor: Editor, file: PsiFile) {
    implicit val typeSystem = project.typeSystem
    def tryToFindBlock(expr: ScExpression): Option[ScExtendsBlock] = {
      blockFor(expr) match {
        case Success(bl) => Some(bl)
        case Failure(e) =>
          CommonRefactoringUtil.showErrorHint(project, editor, e.getMessage, "Create entity quickfix", null)
          None
      }
    }

    if (!ref.isValid) return
    val entityType = typeFor(ref)
    val genericParams = genericParametersFor(ref)
    val parameters = parametersFor(ref)

    val placeholder = if (entityType.isDefined) "%s %s%s: Int" else "%s %s%s"
    val unimplementedBody = if (file.scalaLanguageLevel.exists(_ >= Scala_2_10)) " = ???" else ""
    val params = (genericParams ++: parameters).mkString
    val text = placeholder.format(keyword, ref.nameId.getText, params) + unimplementedBody

    val block = ref match {
      case it if it.isQualified => ref.qualifier.flatMap(tryToFindBlock)
      case Parent(infix: ScInfixExpr) => tryToFindBlock(infix.getBaseExpr)
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

      addTypeParametersToTemplate(entity, builder)
      addParametersToTemplate(entity, builder)
      addQmarksToTemplate(entity, builder)

      CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(entity)

      val template = builder.buildTemplate()

      val isScalaConsole = file.getName == ScalaLanguageConsoleView.SCALA_CONSOLE
      if (!isScalaConsole) {
        val newEditor = positionCursor(entity.getLastChild)
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

  private def blockFor(exp: ScExpression)
                      (implicit typeSystem: TypeSystem): Try[ScExtendsBlock] = {
    object ParentExtendsBlock {
      def unapply(e: PsiElement): Option[ScExtendsBlock] = Option(PsiTreeUtil.getParentOfType(exp, classOf[ScExtendsBlock]))
    }

    exp match {
      case InstanceOfClass(td: ScTemplateDefinition) => Success(td.extendsBlock)
      case th: ScThisReference if PsiTreeUtil.getParentOfType(th, classOf[ScExtendsBlock], true) != null =>
        th.refTemplate match {
          case Some(ScTemplateDefinition.ExtendsBlock(block)) => Success(block)
          case None =>
            val parentBl = PsiTreeUtil.getParentOfType(th, classOf[ScExtendsBlock], /*strict = */true, /*stopAt = */classOf[ScTemplateDefinition])
            if (parentBl != null) Success(parentBl)
            else Failure(new IllegalStateException("Cannot find template definition for `this` reference"))
        }
      case sup: ScSuperReference =>
        unambiguousSuper(sup) match {
          case Some(ScTemplateDefinition.ExtendsBlock(block)) => Success(block)
          case None => Failure(new IllegalStateException("Cannot find template definition for not-static super reference"))
        }
      case Both(th: ScThisReference, ParentExtendsBlock(block)) => Success(block)
      case Both(ReferenceTarget((_: ScSelfTypeElement)), ParentExtendsBlock(block)) => Success(block)
      case _ => Failure(new IllegalStateException("Cannot find a place to create definition"))
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
    case call: ScMethodCall => call.expectedType().map(_.canonicalText)
    case _ => ref.expectedType().map(_.canonicalText)
  }

  private def parametersFor(ref: ScReferenceExpression): Option[String] = {
    ref.parent.collect {
      case MethodRepr(_, _, Some(`ref`), args) => paramsText(args)
      case (_: ScGenericCall) childOf (MethodRepr(_, _, Some(`ref`), args)) => paramsText(args)
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

  private def unambiguousSuper(supRef: ScSuperReference)
                              (implicit typeSystem: TypeSystem): Option[ScTypeDefinition] = {
    supRef.staticSuper match {
      case Some(ExtractClass(clazz: ScTypeDefinition)) => Some(clazz)
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
