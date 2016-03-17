package org.jetbrains.plugins.scala
package codeInsight.delegate

import com.intellij.codeInsight.generation._
import com.intellij.codeInsight.{CodeInsightBundle, CodeInsightUtilBase}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.{Editor, ScrollType}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi._
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameterClause, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.PhysicalSignature
import org.jetbrains.plugins.scala.lang.psi.{TypeAdjuster, ScalaPsiUtil, types}
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiUtil, types}
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult, StdKinds}
import org.jetbrains.plugins.scala.overrideImplement._
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

import scala.collection.JavaConversions._

/**
* Nikolay.Tropin
* 2014-03-21
*/
class ScalaGenerateDelegateHandler extends GenerateDelegateHandler {

  type ClassMember = overrideImplement.ClassMember

  override def isValidFor(editor: Editor, file: PsiFile): Boolean = hasTargetElements(file, editor)

  override def invoke(@NotNull project: Project, @NotNull editor: Editor, @NotNull file: PsiFile) {
    if (!CodeInsightUtilBase.prepareEditorForWrite(editor)) return
    if (!FileDocumentManager.getInstance.requestWriting(editor.getDocument, project)) return
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    val target = chooseTarget(file, editor, project)
    if (target == null) return
    val candidates = chooseMethods(target, file, editor, project)
    if (candidates == null || candidates.length == 0) return

    val elementAtOffset = file.findElementAt(editor.getCaretModel.getOffset)

    val specifyType = ScalaApplicationSettings.getInstance().SPECIFY_RETURN_TYPE_EXPLICITLY

    inWriteCommandAction(project) {
      try {
        val aClass = classAtOffset(editor.getCaretModel.getOffset, file)
        val generatedMethods = for (member <- candidates) yield {
          val prototype: ScFunctionDefinition =
            ScalaPsiElementFactory.createMethodFromSignature(member.sign, aClass.getManager, specifyType, body = "???")
                  .asInstanceOf[ScFunctionDefinition]
          prototype.setModifierProperty("override", value = member.isOverride)
          val body = methodBody(target, prototype)
          prototype.body.foreach(_.replace(body))
          val genInfo = new ScalaGenerationInfo(member)
          val added = aClass.addMember(prototype, Option(genInfo.findInsertionAnchor(aClass, elementAtOffset)))
                  .asInstanceOf[ScFunctionDefinition]
          if (added.superMethod.nonEmpty) added.setModifierProperty("override", value = true)
          added
        }

        if (!generatedMethods.isEmpty) {
          val firstMethod = generatedMethods(0)
          val body = firstMethod.body.get
          editor.getCaretModel.moveToOffset(body.getTextRange.getStartOffset)
          editor.getScrollingModel.scrollToCaret(ScrollType.RELATIVE)
          editor.getSelectionModel.removeSelection()
        }
        TypeAdjuster.adjustFor(generatedMethods)
      }
      catch {
        case e: IncorrectOperationException => throw new IncorrectOperationException(s"Could not delegate methods to ${target.getText}")
      }
    }
  }

  private def methodBody(delegate: ClassMember, prototype: ScFunction): ScExpression = {
    def typeParameterUsedIn(parameter: ScTypeParam, elements: Seq[PsiElement]) = {
      elements.exists(elem => ReferencesSearch.search(parameter, new LocalSearchScope(elem)).findAll().nonEmpty)
    }
    val typeParamsForCall: String = {
      val typeParams = prototype.typeParameters
      val parametersAndRetType = prototype.parameters ++ prototype.returnTypeElement
      if (typeParams.exists(!typeParameterUsedIn(_, parametersAndRetType))) {
        typeParams.map(_.nameId.getText).mkString("[", ", ", "]")
      }
      else ""
    }
    val dText: String = delegateText(delegate)
    val methodName = prototype.name
    def paramClauseApplicationText(paramClause: ScParameterClause) = {
      paramClause.parameters.map(_.name).mkString("(", ", ", ")")
    }
    val params = prototype.effectiveParameterClauses.map(paramClauseApplicationText).mkString
    ScalaPsiElementFactory.createExpressionFromText(s"$dText.$methodName$typeParamsForCall$params", prototype.getManager)
  }


  private def delegateText(delegate: ClassMember): String = {
    val delegateText = delegate match {
      case field@(_: ScValueMember | _: ScVariableMember | _: JavaFieldMember) => field.asInstanceOf[ScalaNamedMember].name
      case methMember: ScMethodMember =>
        methMember.sign.method match {
          case m: PsiMethod if m.isAccessor => m.getName
          case f: ScFunction if f.isEmptyParen => f.name + "()"
          case f: ScFunction if f.isParameterless => f.name
        }
    }
    delegateText
  }

  @Nullable
  private def chooseMethods(delegate: ClassMember, file: PsiFile, editor: Editor, project: Project)
                           (implicit typeSystem: TypeSystem = project.typeSystem): Array[ScMethodMember] = {
    val delegateType = delegate.asInstanceOf[ScalaTypedMember].scType
    val aClass = classAtOffset(editor.getCaretModel.getOffset, file)
    val tBody = aClass.extendsBlock.templateBody.get
    val place = ScalaPsiElementFactory.createExpressionWithContextFromText(delegateText(delegate), tBody, tBody.getFirstChild)
    if (aClass == null) return null
    val processor = new CompletionProcessor(StdKinds.methodRef, place, false)
    processor.processType(delegateType, place)
    val candidates = processor.candidatesS
    val members = toMethodMembers(candidates, place)

    if (!ApplicationManager.getApplication.isUnitTestMode) {
      val chooser = new ScalaMemberChooser[ScMethodMember](members.toArray, false, true, false, true, aClass)
      chooser.setTitle(CodeInsightBundle.message("generate.delegate.method.chooser.title"))
      chooser.show()
      if (chooser.getExitCode != DialogWrapper.OK_EXIT_CODE) return null
      chooser.getSelectedElements.toBuffer.toArray
    }
    else if (members.nonEmpty) Array(members.head) else Array()
  }

  private def toMethodMembers(candidates: Iterable[ScalaResolveResult], place: PsiElement)
                             (implicit typeSystem: TypeSystem): Seq[ScMethodMember] = {
    object isSuitable {
      def unapply(srr: ScalaResolveResult): Option[PhysicalSignature] = {
        if (srr.implicitConversionClass.nonEmpty || srr.implicitFunction.nonEmpty) return None
        srr.getElement match {
          case meth: PsiMethod if meth.isConstructor || meth.getContainingClass == null => None
          case meth: PsiMethod if meth.getContainingClass.getQualifiedName == CommonClassNames.JAVA_LANG_OBJECT => None
          case meth: PsiMethod if !ResolveUtils.isAccessible(meth, place, forCompletion = true) => None
          case meth: PsiMethod => Some(new PhysicalSignature(meth, srr.substitutor))
          case _ => None
        }
      }
    }

    candidates.toSeq.collect {
      case isSuitable(sign) => new ScMethodMember(sign, isOverride = false)
    }
  }

  @Nullable
  private def chooseTarget(file: PsiFile, editor: Editor, project: Project): ClassMember = {
    val elements: Array[ClassMember] = targetElements(file, editor)
    if (elements == null || elements.length == 0) return null
    if (!ApplicationManager.getApplication.isUnitTestMode) {
      val chooser = new ScalaMemberChooser(elements, false, false, false, false, classAtOffset(editor.getCaretModel.getOffset, file))
      chooser.setTitle(CodeInsightBundle.message("generate.delegate.target.chooser.title"))
      chooser.show()
      if (chooser.getExitCode != DialogWrapper.OK_EXIT_CODE) return null
      val selectedElements = chooser.getSelectedElements
      if (selectedElements != null && selectedElements.size > 0) return selectedElements.get(0)
    }
    else {
      return elements(0)
    }
    null
  }

  private def targetElements(file: PsiFile, editor: Editor): Array[ClassMember] = {
    parentClasses(file, editor).flatMap(targetsIn).toArray
  }

  private def hasTargetElements(file: PsiFile, editor: Editor): Boolean = {
    parentClasses(file, editor).exists(hasTargetsIn)
  }

  private def targetsIn(clazz: ScTemplateDefinition): Seq[ClassMember] = {
    //todo add ScObjectMember for targets
    val allMembers = ScalaOIUtil.allMembers(clazz, withSelfType = true)
            .flatMap(ScalaOIUtil.toClassMember(_, isImplement = false))
    allMembers.toSeq.filter(canBeTargetInClass(_, clazz))
  }

  private def hasTargetsIn(clazz: ScTemplateDefinition): Boolean = {
    for {
      m <- ScalaOIUtil.allMembers(clazz, withSelfType = true)
      cm <- ScalaOIUtil.toClassMember(m, isImplement = false)
    } {
      if (canBeTargetInClass(cm, clazz)) return true
    }
    false
  }

  private def canBeTargetInClass(member: ClassMember, clazz: ScTemplateDefinition): Boolean = member match {
    case ta: ScAliasMember => false
    case typed: ScalaTypedMember if typed.scType == types.Unit => false
    case method: ScMethodMember =>
      method.getElement match {
        case m: PsiMethod if {val cl = m.getContainingClass; cl != null && cl.getQualifiedName == CommonClassNames.JAVA_LANG_OBJECT} => false
        case f: ScFunction => (f.isParameterless || f.isEmptyParen) && ResolveUtils.isAccessible(f, clazz, forCompletion = false)
        case m: PsiMethod => m.isAccessor && ResolveUtils.isAccessible(m, clazz, forCompletion = false)
        case _ => false
      }
    case v @ (_: ScValueMember | _: ScVariableMember | _: JavaFieldMember)
      if ResolveUtils.isAccessible(v.getElement, clazz, forCompletion = false) => true
    case _ => false
  }

  private def classAtOffset(offset: Int, file: PsiFile) = {
    val td = PsiTreeUtil.getContextOfType(file.findElementAt(offset), classOf[ScTemplateDefinition])
    if (td == null || td.extendsBlock.templateBody.isEmpty) null
    else td
  }

  private def parentClasses(file: PsiFile, editor: Editor): Seq[ScTemplateDefinition] = {
    val closestClass = classAtOffset(editor.getCaretModel.getOffset, file)
    if (closestClass == null) return Seq.empty

    closestClass +: closestClass.parentsInFile.toSeq.collect {case td: ScTemplateDefinition => td}
  }

}
