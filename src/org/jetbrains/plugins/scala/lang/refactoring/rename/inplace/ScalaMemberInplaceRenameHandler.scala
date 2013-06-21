package org.jetbrains.plugins.scala
package lang.refactoring.rename.inplace

import com.intellij.refactoring.rename.inplace.{InplaceRefactoring, MemberInplaceRenamer, MemberInplaceRenameHandler}
import com.intellij.psi._
import com.intellij.openapi.editor.Editor
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTrait, ScObject, ScClass}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import com.intellij.openapi.actionSystem.DataContext
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import com.intellij.ui.components.JBList
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.util.{PsiTreeUtil, PsiUtilBase}


/**
 * Nikolay.Tropin
 * 6/20/13
 */
class ScalaMemberInplaceRenameHandler extends MemberInplaceRenameHandler{

  def renameProcessor(element: PsiElement): RenamePsiElementProcessor = {
    val processor = if (element != null) RenamePsiElementProcessor.forElement(element) else null
    if (processor != RenamePsiElementProcessor.DEFAULT) processor else null
  }

  override def isAvailable(element: PsiElement, editor: Editor, file: PsiFile): Boolean = {
    val processor = renameProcessor(element)
    editor.getSettings.isVariableInplaceRenameEnabled && processor != null && processor.canProcessElement(element)
  }

  protected override def createMemberRenamer(substituted: PsiElement,
                                             elementToRename: PsiNameIdentifierOwner,
                                             editor: Editor): MemberInplaceRenamer = {
    substituted match {
      case clazz: PsiClass =>
        val companion = ScalaPsiUtil.getBaseCompanionModule(clazz)
        if (companion.isDefined) new ScalaMemberInplaceRenamer(clazz, companion.get, editor)
        else new ScalaMemberInplaceRenamer(clazz, clazz, editor)
      case subst: PsiNamedElement => new ScalaMemberInplaceRenamer(elementToRename, subst, editor)
      case _ => throw new IllegalArgumentException("Substituted element for renaming has no name")
    }
  }

  override def doRename(elementToRename: PsiElement, editor: Editor, dataContext: DataContext): InplaceRefactoring = {
    def specialMethodPopup(fun: ScFunction) {
      val clazz = fun.containingClass
      val clazzType = clazz match {
        case _: ScObject => "object"
        case _: ScClass => "class"
        case _: ScTrait => "trait"
      }
      val title = ScalaBundle.message("rename.special.method.title")
      val renameClass = ScalaBundle.message("rename.special.method.rename.class", clazzType)
      val cancel = ScalaBundle.message("rename.special.method.cancel")
      val list: JBList = new JBList(renameClass, cancel)
      JBPopupFactory.getInstance.createListPopupBuilder(list)
              .setTitle(title)
              .setMovable(false)
              .setResizable(false)
              .setRequestFocus(true)
              .setItemChoosenCallback(new Runnable {
        def run() {
          list.getSelectedValue match {
            case s: String if s == renameClass =>
              new DialogRenamer().doDialogRename(clazz, editor.getProject, null, editor)
            case s: String if s == cancel =>
          }
        }
      }).createPopup.showInBestPositionFor(editor)
    }

    val selected = PsiUtilBase.getElementAtCaret(editor)
    elementToRename match {
      case fun: ScFunction
        if (Seq("apply", "unapply", "unapplySeq").contains(fun.name) || fun.isConstructor) &&
                PsiTreeUtil.isAncestor(elementToRename, selected, false) =>
          specialMethodPopup(fun)
          null
      case _ => super.doRename(elementToRename, editor, dataContext)
    }

  }
}
