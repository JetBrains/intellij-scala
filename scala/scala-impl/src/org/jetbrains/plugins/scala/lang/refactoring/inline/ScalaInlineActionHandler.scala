package org.jetbrains.plugins.scala.lang.refactoring.inline

import com.intellij.lang.Language
import com.intellij.lang.refactoring.InlineActionHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.{Condition, NlsContexts}
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiReference}
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.util.FilteredQuery
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScStableReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDeclaration, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.statistics.ScalaRefactoringUsagesCollector
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaLanguage}

import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.chaining.scalaUtilChainingOps

abstract class ScalaInlineActionHandler extends InlineActionHandler {
  protected def helpId: String

  @NlsContexts.DialogTitle
  protected def refactoringName: String

  protected def canInlineScalaElement(scalaElement: ScalaPsiElement): Boolean

  protected def inlineScalaElement(scalaElement: ScalaPsiElement)(implicit project: Project, editor: Editor): Unit

  final override def isEnabledForLanguage(language: Language): Boolean = language.isKindOf(ScalaLanguage.INSTANCE)

  final override def canInlineElement(element: PsiElement): Boolean = element match {
    case scalaElement: ScalaPsiElement =>
      canInlineScalaElement(scalaElement)
    case _ => false
  }

  final override def getActionName(element: PsiElement): String = refactoringName

  final override def inlineElement(project0: Project, editor0: Editor, element: PsiElement): Unit = {
    implicit val project: Project = project0
    implicit val editor: Editor = editor0

    ScalaRefactoringUsagesCollector.logInline(project)

    element match {
      case typedDef: ScTypedDefinition if isFunctionalType(typedDef) =>
        showErrorHint(ScalaBundle.message("cannot.inline.value.functional.type"))
      case named: ScNamedElement if fromDifferentFile(editor, named) =>
        showErrorHint(ScalaBundle.message("cannot.inline.different.files"))
      case named: ScNamedElement if ScalaPsiUtil.isImplicit(named) =>
        showErrorHint(ScalaBundle.message("cannot.inline.implicit.element"))
      case named: ScNamedElement if !usedInSameClassOnly(named) =>
        showErrorHint(ScalaBundle.message("cannot.inline.used.outside.class"))
      case scalaElement: ScalaPsiElement if checkWritable(scalaElement) =>
        inlineScalaElement(scalaElement)
      case _ =>
    }
  }

  final protected def showErrorHint(@NlsContexts.DialogMessage message: String)(implicit project: Project, editor: Editor): Unit =
    CommonRefactoringUtil.showErrorHint(project, editor, message, refactoringName, helpId)

  final protected def showDialog(dialog: ScalaInlineDialog): Unit =
    if (!ApplicationManager.getApplication.isUnitTestMode)
      dialog.show()
    else {
      try dialog.doAction()
      finally dialog.close(DialogWrapper.OK_EXIT_CODE, true)
    }

  protected def validateReferences(element: ScNamedElement)(implicit project: Project, editor: Editor): Boolean = {
    val refs = ReferencesSearch.search(element, element.getUseScope).findAll().asScala

    checkUsed(refs) &&
      checkNotUsedInStableRef(refs)
  }

  protected def checkUsed(refs: Iterable[PsiReference])(implicit project: Project, editor: Editor): Boolean =
    !refs.isEmpty.tap { unused =>
      if (unused) {
        showErrorHint(ScalaBundle.message("cannot.inline.never.used"))
      }
    }

  protected def checkNotUsedInStableRef(refs: Iterable[PsiReference])(implicit project: Project, editor: Editor): Boolean =
    !refs.iterator
      .flatMap(_.getElement.nonStrictParentOfType(Seq(classOf[ScStableCodeReference], classOf[ScStableReferencePattern])))
      .hasNext
      .tap { used =>
        if (used) {
          showErrorHint(ScalaBundle.message("cannot.inline.stable.reference"))
        }
      }

  protected def checkWritable(element: ScalaPsiElement)(implicit project: Project, editor: Editor): Boolean =
    element.isWritable.tap { isWritable =>
      if (!isWritable) {
        showErrorHint(ScalaBundle.message("cannot.inline.read.only"))
      }
    }

  protected def isFunctionalType(typedDef: ScTypedDefinition): Boolean =
    FunctionType.unapply(typedDef.`type`().getOrAny).exists(_._2.nonEmpty) &&
      (typedDef match {
        case _: ScFunctionDeclaration | _: ScFunctionDefinition => false
        case _ => true
      })

  private def fromDifferentFile(editor: Editor, named: ScNamedElement): Boolean = {
    named.getContainingFile != PsiDocumentManager.getInstance(editor.getProject).getPsiFile(editor.getDocument)
  }

  private def usedInSameClassOnly(named: ScNamedElement): Boolean = {
    named.nameContext match {
      case member: ScMember =>
        val memberContainingClass = member.containingClass
        //NOTE: it will return true for Scala 3 top-level definitions, this should be probably handled better ?
        if (memberContainingClass == null)
          return true

        val allReferences = ReferencesSearch.search(named, named.getUseScope)
        val notInSameClass: Condition[PsiReference] = ref => {
          val isInSameClass = PsiTreeUtil.isAncestor(memberContainingClass, ref.getElement, true)
          !isInSameClass
        }

        val notInSameClassQuery = new FilteredQuery[PsiReference](allReferences, notInSameClass)

        val firstUsageOutsideClass = notInSameClassQuery.findFirst()
        firstUsageOutsideClass == null
      case _ => true
    }
  }
}
