package org.jetbrains.plugins.scala.annotator.gutter

import _root_.scala.collection.mutable.HashSet
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.daemon.impl.{PsiElementListNavigator, LineMarkerNavigator}
import com.intellij.codeInsight.daemon.{GutterIconNavigationHandler, DaemonBundle}
import com.intellij.ide.util.{PsiClassListCellRenderer, PsiElementListCellRenderer}
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.openapi.util.Iconable
import com.intellij.psi._
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.ui.awt.RelativePoint
import java.util.Arrays
import javax.swing.Icon
import com.intellij.util.NullableFunction
import java.awt.event.MouseEvent
import lang.psi.api.statements.ScFunction
import lang.psi.api.toplevel.typedef.{ScClass, ScTrait, ScMember, ScObject}
import lang.psi.ScalaPsiUtil
import lang.psi.types.FullSignature

/**
 * User: Alexander Podkhalyuzin
 * Date: 09.11.2008
 */

object ScalaMarkerType {
  val OVERRIDING_MEMBER = ScalaMarkerType(new NullableFunction[PsiElement, String] {
    def fun(element: PsiElement): String = {
      element match {
        case method: ScFunction => {
          val sigs = method.superSignatures
          assert(sigs.length != 0)
          val clazz = sigs(0).clazz
          assert(clazz != null)
          if (!GutterUtil.isOverrides(element)) ScalaBundle.message("implements.method.from.super", Array[Object](clazz.getQualifiedName))
          else ScalaBundle.message("overrides.method.from.super", Array[Object](clazz.getQualifiedName))
        }
        case _ => null
      }
    }
  }, new GutterIconNavigationHandler[PsiElement]{
    def navigate(e: MouseEvent, element: PsiElement) {
      element match {
        case method: ScFunction => {
          val signatures = method.superSignatures
          val elems = signatures.map(_.element)
          elems.length match {
            case 0 =>
            case 1 =>
              if (elems(0).canNavigate) elems(0).navigate(true)
            case _ => {
              val gotoDeclarationPopup = NavigationUtil.getPsiElementPopup(elems.toArray, new ScCellRenderer,
              ScalaBundle.message("goto.override.method.declaration", Array[Object]()))
              gotoDeclarationPopup.show(new RelativePoint(e))
            }
          }
        }
        case _ =>
      }
    }
  })

  val SUBCLASSED_CLASS = ScalaMarkerType(new NullableFunction[PsiElement, String]{
    def fun(element: PsiElement): String = {
      if (!element.isInstanceOf[PsiClass]) return null
      element match {
        case _: ScTrait => ScalaBundle.message("trait.has.implementations", Array[Object]())
        case _: ScObject => ScalaBundle.message("object.has.subclasses", Array[Object]())
        case _ => ScalaBundle.message("class.has.subclasses", Array[Object]())
      }
    }
  }, new GutterIconNavigationHandler[PsiElement] {
    def navigate(e: MouseEvent, element: PsiElement): Unit = {
      val clazz = element match {
        case x: PsiClass => x
        case _ => return
      }
      val inheritors = ClassInheritorsSearch.search(clazz, clazz.getUseScope, true).toArray(PsiClass.EMPTY_ARRAY)
      if (inheritors.length == 0) return
      val title = clazz match {
        case _: ScTrait => ScalaBundle.message("goto.implementation.chooser.title", Array[Object](clazz.getName, "" + inheritors.length))
        case _ => ScalaBundle.message("navigation.title.subclass", Array[Object](clazz.getName, "" + inheritors.length))
      }
      val renderer = new PsiClassListCellRenderer
      Arrays.sort(inheritors, renderer.getComparator)
      PsiElementListNavigator.openTargets(e, inheritors.map(_.asInstanceOf[NavigatablePsiElement]), title, renderer)
    }
  })

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

case class ScalaMarkerType(val fun: com.intellij.util.Function[PsiElement,String], val handler: GutterIconNavigationHandler[PsiElement])