package org.jetbrains.plugins.scala
package lang.refactoring.rename.inplace

import com.intellij.refactoring.rename.inplace.{InplaceRefactoring, MemberInplaceRenamer, MemberInplaceRenameHandler}
import com.intellij.psi._
import com.intellij.openapi.editor.Editor
import com.intellij.refactoring.rename.{PsiElementRenameHandler, RenamePsiElementProcessor}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTrait, ScObject, ScClass}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import com.intellij.openapi.actionSystem.DataContext
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import com.intellij.ui.components.JBList
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.util.PsiUtilBase
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.refactoring.rename.ScalaRenameUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import org.jetbrains.plugins.scala.lang.psi.light.{LightScalaMethod, PsiClassWrapper}
import javax.swing.JList

/**
 * Nikolay.Tropin
 * 6/20/13
 */
class ScalaInplaceRenameHandler extends MemberInplaceRenameHandler{

  def renameProcessor(element: PsiElement): RenamePsiElementProcessor = {
    val isScalaElement = element match {
      case null => false
      case _: LightScalaMethod | _: PsiClassWrapper => true
      case _  => element.getLanguage.isInstanceOf[ScalaLanguage]
    }
    val processor = if (isScalaElement) RenamePsiElementProcessor.forElement(element) else null
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
      case clazz: PsiClass if elementToRename.isInstanceOf[PsiClassWrapper] =>
        new ScalaInplaceRenamer(elementToRename, clazz, editor)
      case clazz: PsiClass =>
        val companion = ScalaPsiUtil.getBaseCompanionModule(clazz)
        new ScalaInplaceRenamer(clazz, companion.getOrElse(clazz), editor)
      case subst: PsiNamedElement => new ScalaInplaceRenamer(elementToRename, subst, editor)
      case _ => throw new IllegalArgumentException("Substituted element for renaming has no name")
    }
  }

  override def doRename(elementToRename: PsiElement, editor: Editor, dataContext: DataContext): InplaceRefactoring = {
    def showSubstitutePopup(title: String, positive: String, subst: => PsiNamedElement): Unit = {
      val cancel = ScalaBundle.message("rename.cancel")
      val list: JList[_] = new JBList(positive, cancel).asInstanceOf[JList[_]]
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
    ScalaRenameUtil.findSubstituteElement(elementToRename) match {
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

  protected def doDialogRename(element: PsiElement, project: Project, nameSuggestionContext: PsiElement, editor: Editor): Unit = {
    PsiElementRenameHandler.rename(element, project, nameSuggestionContext, editor)
  }

}
