package org.jetbrains.plugins.scala.annotator.gutter


import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.ide.util.{EditSourceUtil, PsiElementListCellRenderer}
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.editor.markup.GutterIconRenderer

import com.intellij.openapi.util.{Iconable, IconLoader}


import com.intellij.psi.util.PsiTreeUtil

import com.intellij.psi.{PsiMethod, PsiElement, PsiNamedElement, PsiClass}
import com.intellij.ui.awt.RelativePoint
import java.awt.event.MouseEvent
import javax.swing.Icon
import lang.psi.api.statements.ScTypeAlias
import lang.psi.api.toplevel.typedef.ScMember
import lang.psi.ScalaPsiUtil
import lang.psi.types.FullSignature

/**
 * User: Alexander Podkhalyuzin
 * Date: 21.09.2008
 */

class OverrideGutter(sigs: Seq[FullSignature], vals: Seq[PsiNamedElement], types: Seq[ScTypeAlias], isImplements: Boolean) extends GutterIconRenderer {
  def getIcon: Icon = if (isImplements) IconLoader.getIcon("/gutter/implementingMethod.png");
                      else IconLoader.getIcon("/gutter/overridingMethod.png")
  override lazy val getTooltipText: String = {
    assert(sigs.length + vals.length + types.length > 0)
    val clazz = if (sigs.length > 0) sigs(0).clazz
    else if (vals.length > 0) PsiTreeUtil.getParentOfType(vals(0), classOf[PsiClass])
    else PsiTreeUtil.getParentOfType(types(0), classOf[PsiClass])
    assert(clazz != null)
    if (isImplements) ScalaBundle.message("implements.method.from.super", Array[Object](clazz.getQualifiedName))
    else ScalaBundle.message("overrides.method.from.super", Array[Object](clazz.getQualifiedName))
  }

  override lazy val getClickAction: AnAction = new AnAction {
    def actionPerformed(e: AnActionEvent) {
      sigs.length + vals.length + types.length match {
        case 0 =>
        case 1 => {
          if (sigs.length > 0 && sigs(0).element.canNavigateToSource) sigs(0).element.navigate(true)
          else if (vals.length > 0 && EditSourceUtil.canNavigate(vals(0))) EditSourceUtil.getDescriptor(vals(0)).navigate(true)
          else if (types(0).canNavigateToSource) types(0).navigate(true)
        }
        case _ => {
          val elems = sigs.map{_.element}.toArray ++ vals.toArray ++ types.toArray
          val gotoDeclarationPopup = NavigationUtil.getPsiElementPopup(elems, new ScCellRenderer,
              ScalaBundle.message("goto.override.method.declaration", Array[Object]()))
          gotoDeclarationPopup.show(new RelativePoint(e.getInputEvent.asInstanceOf[MouseEvent]))
        }
      }
    }
  }

  private class ScCellRenderer extends PsiElementListCellRenderer[PsiElement] {
    def getElementText(element: PsiElement): String = {
      element match {
        case method: PsiMethod if method.getContainingClass != null => {
          val presentation = method.getContainingClass.getPresentation
          presentation.getPresentableText + " " + presentation.getLocationString
        }
        case x: PsiNamedElement if ScalaPsiUtil.nameContext(x) != null => {
          val presentation = ScalaPsiUtil.nameContext(x).asInstanceOf[ScMember].getContainingClass.getPresentation
          presentation.getPresentableText + " " + presentation.getLocationString
        }
        case _ => element.getText().substring(0, Math.min(element.getText().length, 20))
      }
    }

    def getContainerText(psiElement: PsiElement, s: String) = null

    def getIconFlags: Int = Iconable.ICON_FLAG_CLOSED


    override def getIcon(element: PsiElement): Icon = {
      element match {
        case _: PsiMethod => super.getIcon(element)
        case x: PsiNamedElement if ScalaPsiUtil.nameContext(x) != null => ScalaPsiUtil.nameContext(x).getIcon(getIconFlags)
        case _ => super.getIcon(element)
      }
    }
  }
}