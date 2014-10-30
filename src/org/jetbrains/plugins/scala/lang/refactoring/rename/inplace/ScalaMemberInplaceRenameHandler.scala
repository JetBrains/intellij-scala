package org.jetbrains.plugins.scala
package lang.refactoring.rename.inplace

import com.intellij.internal.statistic.UsageTrigger
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi._
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.util.PsiUtilBase
import com.intellij.refactoring.rename.inplace.{InplaceRefactoring, MemberInplaceRenameHandler, MemberInplaceRenamer}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.refactoring.rename.ScalaRenameUtil
import org.jetbrains.plugins.scala.util.JListCompatibility

/**
 * Nikolay.Tropin
 * 6/20/13
 */
class ScalaMemberInplaceRenameHandler extends MemberInplaceRenameHandler with ScalaInplaceRenameHandler {

  override def isAvailable(element: PsiElement, editor: Editor, file: PsiFile): Boolean = {
    val processor = renameProcessor(element)
    editor.getSettings.isVariableInplaceRenameEnabled && processor != null && processor.canProcessElement(element) && 
            !element.getUseScope.isInstanceOf[LocalSearchScope]
  }


  override def invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) = {
    UsageTrigger.trigger(ScalaBundle.message("rename.member.id"))
    super.invoke(project, editor, file, dataContext)
  }

  protected override def createMemberRenamer(substituted: PsiElement,
                                             elementToRename: PsiNameIdentifierOwner,
                                             editor: Editor): MemberInplaceRenamer = {
    substituted match {
      case clazz: PsiClass if elementToRename.isInstanceOf[PsiClassWrapper] =>
        new ScalaMemberInplaceRenamer(elementToRename, clazz, editor)
      case clazz: PsiClass =>
        val companion = ScalaPsiUtil.getBaseCompanionModule(clazz)
        new ScalaMemberInplaceRenamer(clazz, companion.getOrElse(clazz), editor)
      case subst: PsiNamedElement => new ScalaMemberInplaceRenamer(elementToRename, subst, editor)
      case _ => throw new IllegalArgumentException("Substituted element for renaming has no name")
    }
  }

  override def doRename(elementToRename: PsiElement, editor: Editor, dataContext: DataContext): InplaceRefactoring = {
    def showSubstitutePopup(title: String, positive: String, subst: => PsiNamedElement): Unit = {
      val cancel = ScalaBundle.message("rename.cancel")
      val list = JListCompatibility.createJBListFromListData(positive, cancel)
      JBPopupFactory.getInstance.createListPopupBuilder(list)
              .setTitle(title)
              .setMovable(false)
              .setResizable(false)
              .setRequestFocus(true)
              .setItemChoosenCallback(new Runnable {
        def run(): Unit = {
          list.getSelectedValue match {
            case s: String if s == positive =>
              val file = subst.getContainingFile.getVirtualFile
              if (FileDocumentManager.getInstance.getDocument(file) == editor.getDocument) {
                editor.getCaretModel.moveToOffset(subst.getTextOffset)
                doRename(subst, editor, dataContext)
              } else {
                doDialogRename(subst, editor.getProject, null, editor)
              }
            case s: String if s == cancel =>
          }
        }
      }).createPopup.showInBestPositionFor(editor)
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

    val atCaret = PsiUtilBase.getElementAtCaret(editor)
    val selected = ScalaPsiUtil.getParentOfType(atCaret, classOf[ScReferenceElement], classOf[ScNamedElement])
    val nameId = selected match {
      case ref: ScReferenceElement => ref.nameId
      case named: ScNamedElement => named.nameId
      case _ => null
    }
    elementToRename match {
      case fun: ScFunction
        if nameId != null && nameId.getText == fun.name && Seq("apply", "unapply", "unapplySeq").contains(fun.name) || fun.isConstructor =>
          specialMethodPopup(fun)
          null
      case elem =>
        if (nameId != null) nameId.getParent match {
          case ref: ScReferenceElement if ScalaRenameUtil.isAliased(ref) =>
            aliasedElementPopup(ref)
            return null
          case _ =>
        }
        super.doRename(elem, editor, dataContext)
    }
  }

}
