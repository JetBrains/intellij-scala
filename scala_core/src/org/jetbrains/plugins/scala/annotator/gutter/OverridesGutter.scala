package org.jetbrains.plugins.scala.annotator.gutter


import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.editor.markup.GutterIconRenderer

import com.intellij.openapi.util.{Iconable, IconLoader}
import com.intellij.psi.{PsiMethod, PsiElement}
import com.intellij.ui.awt.RelativePoint
import java.awt.event.MouseEvent
import javax.swing.Icon
import lang.psi.ScalaPsiUtil

/**
 * User: Alexander Podkhalyuzin
 * Date: 21.09.2008
 */

class OverrideGutter(methods: Seq[PsiMethod], isImplements: Boolean) extends GutterIconRenderer {
  def getIcon: Icon = if (isImplements) IconLoader.getIcon("/gutter/implementingMethod.png");
                      else IconLoader.getIcon("/gutter/overridingMethod.png")
  override lazy val getTooltipText: String = {
    assert(methods.length > 0)
    val clazz = methods(0).getContainingClass
    assert(clazz != null)
    if (isImplements) ScalaBundle.message("implements.method.from.super", Array[Object](clazz.getQualifiedName))
    else ScalaBundle.message("overrides.method.from.super", Array[Object](clazz.getQualifiedName))
  }


  override lazy val getClickAction: AnAction = new AnAction {
    def actionPerformed(e: AnActionEvent) {
      methods.length match {
        case 0 =>
        case 1 => {
          if (methods(0).canNavigateToSource) methods(0).navigate(true)
        }
        case _ => {
          val gotoDeclarationPopup = NavigationUtil.getPsiElementPopup(methods.toArray, new ScCellRenderer,
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
        case _ => element.getText().substring(0, Math.min(element.getText().length, 20))
      }
    }

    def getContainerText(psiElement: PsiElement, s: String) = null

    def getIconFlags: Int = Iconable.ICON_FLAG_CLOSED
  }
}