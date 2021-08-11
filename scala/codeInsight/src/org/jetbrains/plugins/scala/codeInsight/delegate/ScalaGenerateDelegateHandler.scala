package org.jetbrains.plugins.scala
package codeInsight
package delegate

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.generation._
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
import org.jetbrains.plugins.scala.lang.psi.TypeAdjuster
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameterClause, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.types.PhysicalMethodSignature
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult, StdKinds}
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.TypeAnnotationUtil

import scala.annotation.nowarn
import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters._

/**
* Nikolay.Tropin
* 2014-03-21
*/
final class ScalaGenerateDelegateHandler extends GenerateDelegateHandler {

  import overrideImplement._

  type ClassMember = overrideImplement.ClassMember

  override def isValidFor(editor: Editor, file: PsiFile): Boolean =
    targetElements(file, editor).nonEmpty

  override def invoke(@NotNull project: Project, @NotNull editor: Editor, @NotNull file: PsiFile): Unit = {
    if (!FileDocumentManager.getInstance.requestWriting(editor.getDocument, project)) return
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    val target = chooseTarget(file, editor)
    if (target == null) return
    val candidates = chooseMethods(target, file, editor, project)
    if (candidates == null || candidates.isEmpty) return

    val elementAtOffset = file.findElementAt(editor.getCaretModel.getOffset)

    inWriteCommandAction {
      try {
        val aClass = classAtOffset(editor.getCaretModel.getOffset, file)
        val generatedMethods = for (member <- candidates) yield {
          val ScMethodMember(signature, isOverride) = member
          val prototype: ScFunctionDefinition =
            createMethodFromSignature(signature, body = "???")(aClass.getManager).asInstanceOf[ScFunctionDefinition]
          TypeAnnotationUtil.removeTypeAnnotationIfNeeded(prototype, ScalaGenerationInfo.typeAnnotationsPolicy)
          prototype.setModifierProperty("override", value = isOverride)
          val body = methodBody(target, prototype)
          prototype.body.foreach(_.replace(body))
          val genInfo = new ScalaGenerationInfo(member)
          val added = aClass.addMember(prototype, Option(genInfo.findInsertionAnchor(aClass, elementAtOffset)))
                  .asInstanceOf[ScFunctionDefinition]
          if (added.superMethod.nonEmpty) added.setModifierProperty("override")
          added
        }

        if (generatedMethods.nonEmpty) {
          val firstMethod = generatedMethods(0)
          val body = firstMethod.body.get
          editor.getCaretModel.moveToOffset(body.getTextRange.getStartOffset)
          editor.getScrollingModel.scrollToCaret(ScrollType.RELATIVE)
          editor.getSelectionModel.removeSelection()
        }
        TypeAdjuster.adjustFor(generatedMethods)
      }
      catch {
        case _: IncorrectOperationException => throw new IncorrectOperationException(s"Could not delegate methods to ${target.getText}")
      }
    }(project)
  }

  private def methodBody(delegate: ClassMember, prototype: ScFunction): ScExpression = {
    def typeParameterUsedIn(parameter: ScTypeParam, elements: Seq[PsiElement]) = {
      elements.exists(elem =>
        !ReferencesSearch
          .search(parameter, new LocalSearchScope(elem))
          .findAll()
          .isEmpty
      )
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
    createExpressionFromText(s"$dText.$methodName$typeParamsForCall$params")(prototype.getManager)
  }


  private def delegateText(delegate: ClassMember): String = {
    val delegateText = (delegate match {
      case field: ScalaFieldMember => field.name
      case ScMethodMember(PhysicalMethodSignature(method, _), _) =>
        method match {
          case m: PsiMethod if m.isAccessor => m.name
          case f: ScFunction if f.isEmptyParen => f.name + "()"
          case f: ScFunction if f.isParameterless => f.name
        }
    }): @nowarn("msg=exhaustive")
    delegateText
  }

  @Nullable
  private def chooseMethods(delegate: ClassMember, file: PsiFile, editor: Editor, project: Project): ArraySeq[ScMethodMember] = {
    val delegateType = delegate.asInstanceOf[ScalaTypedMember].scType
    val aClass = classAtOffset(editor.getCaretModel.getOffset, file)
    val tBody = aClass.extendsBlock.templateBody.get
    val place = createExpressionWithContextFromText(delegateText(delegate), tBody, tBody.getFirstChild)
    if (aClass == null) return null

    val candidates = CompletionProcessor.variants(delegateType, place)
    val members = toMethodMembers(candidates, place)

    if (!ApplicationManager.getApplication.isUnitTestMode) {
      val chooser = new ScalaMemberChooser[ScMethodMember](members, false, true, false, true, false, aClass)
      chooser.setTitle(CodeInsightBundle.message("generate.delegate.method.chooser.title"))
      chooser.show()
      if (chooser.getExitCode != DialogWrapper.OK_EXIT_CODE) return null
      chooser.getSelectedElements.asScala.to(ArraySeq)
    }
    else if (members.nonEmpty) ArraySeq(members.head)
    else ArraySeq()
  }

  private def toMethodMembers(candidates: Iterable[ScalaResolveResult], place: PsiElement): ArraySeq[ScMethodMember] = {
    def toMember(srr: ScalaResolveResult): Option[ScMethodMember] = {
      val ScalaResolveResult(element, subst) = srr
      implicit val ctx: ProjectContext = element

      if (srr.implicitFunction.nonEmpty) return None
      element match {
        case method: PsiMethod if method.isConstructor || method.containingClass == null => None
        case method: PsiMethod if method.containingClass.qualifiedName == CommonClassNames.JAVA_LANG_OBJECT => None
        case method: PsiMethod if !ResolveUtils.isAccessible(method, place, forCompletion = true) => None
        case method: PsiMethod => Some(ScMethodMember(method, subst))
        case _ => None
      }
    }

    candidates.iterator.flatMap(toMember).to(ArraySeq)
  }

  @Nullable
  private def chooseTarget(file: PsiFile, editor: Editor): ClassMember = {
    val elements = targetElements(file, editor)
    if (elements.isEmpty) null
    else if (ApplicationManager.getApplication.isUnitTestMode) elements.head
    else {
      val chooser = new ScalaMemberChooser(elements, false, false, false, false, false, classAtOffset(editor.getCaretModel.getOffset, file))
      chooser.setTitle(CodeInsightBundle.message("generate.delegate.target.chooser.title"))
      chooser.show()

      if (chooser.getExitCode != DialogWrapper.OK_EXIT_CODE) return null
      val selectedElements = chooser.getSelectedElements
      if (selectedElements != null && selectedElements.size > 0) selectedElements.get(0)
      else null
    }
  }

  private def targetElements(file: PsiFile, editor: Editor): ArraySeq[ClassMember] =
    parentClasses(file, editor).iterator
      .flatMap(targetsIn)
      .to(ArraySeq)
      .sortBy(m => (m.getPsiElement.getContainingFile.name, m.getPsiElement.getTextRange.getStartOffset))

  // todo add ScObjectMember for targets
  private def targetsIn(clazz: ScTemplateDefinition): Seq[ClassMember] =
    ScalaOIUtil.getAllMembersToOverride(clazz)
      .filter(canBeTargetInClass(_, clazz))

  private def canBeTargetInClass(member: ClassMember, clazz: ScTemplateDefinition): Boolean = member match {
    case _: ScAliasMember => false
    case typed: ScalaTypedMember if typed.scType.isUnit => false
    case ScMethodMember(PhysicalMethodSignature(method, _), _) =>
      method match {
        case m: PsiMethod if {val cl = m.containingClass; cl != null && cl.qualifiedName == CommonClassNames.JAVA_LANG_OBJECT} => false
        case f: ScFunction => (f.isParameterless || f.isEmptyParen) && ResolveUtils.isAccessible(f, clazz)
        case m: PsiMethod => m.isAccessor && ResolveUtils.isAccessible(m, clazz)
        case _ => false
      }
    case fieldMember: ScalaFieldMember => ResolveUtils.isAccessible(fieldMember.getElement, clazz)
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

    closestClass.withParentsInFile.toSeq.collect {case td: ScTemplateDefinition => td}
  }

}
