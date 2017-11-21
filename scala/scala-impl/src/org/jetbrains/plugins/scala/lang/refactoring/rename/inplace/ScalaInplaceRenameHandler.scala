package org.jetbrains.plugins.scala
package lang.refactoring.rename.inplace

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.util.PsiUtilBase.getElementAtCaret
import com.intellij.psi.{PsiElement, PsiNamedElement}
import com.intellij.refactoring.rename.inplace.InplaceRefactoring
import com.intellij.refactoring.rename.{PsiElementRenameHandler, RenamePsiElementProcessor}
import org.jetbrains.plugins.scala.extensions.{Both, PsiElementExt, callbackInTransaction}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.light.{PsiClassWrapper, PsiMethodWrapper}
import org.jetbrains.plugins.scala.lang.refactoring.rename.ScalaRenameUtil
import org.jetbrains.plugins.scala.util.JListCompatibility

/**
 * Nikolay.Tropin
 * 1/20/14
 */
trait ScalaInplaceRenameHandler {

  def renameProcessor(element: PsiElement): RenamePsiElementProcessor = {
    val isScalaElement = element match {
      case null => false
      case _: PsiMethodWrapper | _: PsiClassWrapper => true
      case _ => element.getLanguage.isKindOf(ScalaLanguage.INSTANCE)
    }
    val processor = if (isScalaElement) RenamePsiElementProcessor.forElement(element) else null
    if (processor != RenamePsiElementProcessor.DEFAULT) processor else null
  }

  protected def doDialogRename(element: PsiElement, project: Project, nameSuggestionContext: PsiElement, editor: Editor): Unit = {
    PsiElementRenameHandler.rename(element, project, nameSuggestionContext, editor)
  }

  def afterElementSubstitution(elementToRename: PsiElement, editor: Editor, dataContext: DataContext)(inplaceRename: PsiElement => InplaceRefactoring): InplaceRefactoring = {
    def showSubstitutePopup(title: String, positive: String, subst: => PsiNamedElement): Unit = {
      val cancel = ScalaBundle.message("rename.cancel")
      val list = JListCompatibility.createJBListFromListData(positive, cancel)
      val callback = callbackInTransaction(editor.getProject) {
        list.getSelectedValue match {
          case s: String if s == positive =>
            val file = subst.getContainingFile.getVirtualFile
            if (FileDocumentManager.getInstance.getDocument(file) == editor.getDocument) {
              editor.getCaretModel.moveToOffset(subst.getTextOffset)
              inplaceRename(subst)
            } else {
              doDialogRename(subst, editor.getProject, null, editor)
            }
          case s: String if s == cancel =>
        }
      }
      JBPopupFactory.getInstance.createListPopupBuilder(list)
        .setTitle(title)
        .setMovable(false)
        .setResizable(false)
        .setRequestFocus(true)
        .setItemChoosenCallback(callback)
        .createPopup.showInBestPositionFor(editor)
    }

    def specialMethodPopup(fun: ScFunction): Unit = {
      val clazz = fun.containingClass
      val clazzType = clazz match {
        case _: ScObject => "object"
        case _: ScClass => "class"
        case _: ScTrait => "trait"
        case _: ScNewTemplateDefinition => "instance"
      }
      val title = ScalaBundle.message("rename.special.method.title")
      val positive = ScalaBundle.message("rename.special.method.rename.class", clazzType)
      showSubstitutePopup(title, positive, ScalaRenameUtil.findSubstituteElement(elementToRename))
    }
    def aliasedElementPopup(ref: ScReferenceElement): Unit = {
      val title = ScalaBundle.message("rename.aliased.title")
      val positive = ScalaBundle.message("rename.aliased.rename.actual")
      showSubstitutePopup(title, positive, ScalaRenameUtil.findSubstituteElement(elementToRename))
    }

    val selected = getElementAtCaret(editor)
      .nonStrictParentOfType(Seq(classOf[ScReferenceElement], classOf[ScNamedElement]))
    val nameId = selected.collect {
      case ref: ScReferenceElement => ref.nameId
      case named: ScNamedElement => named.nameId
    }.orNull

    elementToRename match {
      case Both(`selected`, fun: ScFunction) if Seq("apply", "unapply", "unapplySeq").contains(fun.name) || fun.isConstructor =>
        specialMethodPopup(fun)
        null
      case _ =>
        if (nameId != null) nameId.getParent match {
          case ref: ScReferenceElement if ScalaRenameUtil.isAliased(ref) =>
            aliasedElementPopup(ref)
            return null
          case _ =>
        }
        inplaceRename(ScalaRenameUtil.findSubstituteElement(elementToRename))
    }
  }
}
