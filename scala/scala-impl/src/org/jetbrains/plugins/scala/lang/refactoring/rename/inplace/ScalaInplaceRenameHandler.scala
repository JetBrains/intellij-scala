package org.jetbrains.plugins.scala.lang.refactoring.rename.inplace

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.util.PsiUtilBase.getElementAtCaret
import com.intellij.psi.{PsiElement, PsiNamedElement}
import com.intellij.refactoring.rename.inplace.InplaceRefactoring
import com.intellij.refactoring.rename.{PsiElementRenameHandler, RenamePsiElementProcessor}
import com.intellij.ui.components.JBList
import org.jetbrains.annotations.{Nls, Nullable}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, invokeLaterInTransaction}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScEnd, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScEndImpl.Target
import org.jetbrains.plugins.scala.lang.psi.light.{PsiClassWrapper, PsiMethodWrapper}
import org.jetbrains.plugins.scala.lang.refactoring.rename.{RenameScalaPackageProcessor, ScalaRenameUtil}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaLanguage}

import scala.annotation.nowarn

trait ScalaInplaceRenameHandler {

  protected final def renameProcessor(@Nullable element: PsiElement): RenamePsiElementProcessor = {
    val isScalaElement = element match {
      case null => false
      case _: PsiMethodWrapper[_] | _: PsiClassWrapper => true
      case _ => element.getLanguage.isKindOf(ScalaLanguage.INSTANCE)
    }
    val processor = if (isScalaElement) RenamePsiElementProcessor.forElement(element) else null
    // packages are renamed using the default RenameHandler
    if (processor != RenamePsiElementProcessor.DEFAULT && !processor.is[RenameScalaPackageProcessor]) {
      processor
    } else {
      null
    }
  }

  protected final def isLocal(element: PsiElement): Boolean =
    element.asOptionOf[PsiNamedElement] match {
      case Some(inNameContext(m: ScMember)) => m.isLocal || m.getModifierList.accessModifier.exists(_.isUnqualifiedPrivateOrThis)
      case _ => false
    }

  private def doDialogRename(element: PsiElement, project: Project, nameSuggestionContext: PsiElement, editor: Editor): Unit = {
    PsiElementRenameHandler.rename(element, project, nameSuggestionContext, editor)
  }

  @Nullable
  protected final def afterElementSubstitution(elementToRename: PsiElement, editor: Editor)(inplaceRename: PsiElement => InplaceRefactoring): InplaceRefactoring = {
    def showSubstitutePopup(@Nls title: String, positive: String, subst: => PsiNamedElement): Unit = {
      val cancel = ScalaBundle.message("rename.cancel")
      val list = new JBList[String](positive, cancel)
      val callback: Runnable = () => invokeLaterInTransaction(editor.getProject.unloadAwareDisposable) {
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
        .setItemChosenCallback(callback)
        .createPopup.showInBestPositionFor(editor): @nowarn("cat=deprecation")
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
      showSubstitutePopup(
        title,
        positive,
        ScalaRenameUtil.findSubstituteElement(elementToRename)
          .getOrElse(throw new Exception(s"Cannot find substitute for ${elementToRename.getText}"))
      )
    }
    def aliasedElementPopup(): Unit = {
      val title = ScalaBundle.message("rename.aliased.title")
      val positive = ScalaBundle.message("rename.aliased.rename.actual")
      showSubstitutePopup(
        title,
        positive,
        ScalaRenameUtil.findSubstituteElement(elementToRename)
          .getOrElse(throw new Exception(s"Cannot find substitute for ${elementToRename.getText}"))
      )
    }

    val selected = Option(getElementAtCaret(editor))
      .flatMap(_.nonStrictParentOfType(Seq(classOf[ScReference], classOf[ScNamedElement])))
    val nameId = selected.collect {
      case ref: ScReference => ref.nameId
      case named: ScNamedElement => named.nameId
    }

    val actualElementToRename = elementToRename match {
      case Target(ScEnd(Some(begin), _)) =>
        val element = begin.tag
        if (element.is[ScNamedElement]) {
          editor.getCaretModel.moveToOffset(element.getTextOffset)
          element
        } else {
          begin
        }
      case element => element
    }

    actualElementToRename match {
      case fun: ScFunction if selected.contains(fun) && isSpecial(fun) =>
        specialMethodPopup(fun)
        null
      case e =>
        nameId.map(_.getParent) match {
          case Some(ref: ScReference) if ScalaRenameUtil.isAliased(ref) =>
            aliasedElementPopup()
            null
          case _ =>
            ScalaRenameUtil.findSubstituteElement(e)
              .map { subst =>
                inplaceRename(subst)
              }
              .orNull
        }
    }
  }

  private def isSpecial(f: ScFunction): Boolean = {
    val hasSpecialName = f.name match {
      case "apply" | "unapply" | "unapplySeq" => true
      case _ => false
    }
    hasSpecialName || f.isConstructor
  }
}
