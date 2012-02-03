package org.jetbrains.plugins.scala
package annotator
package gutter

import _root_.scala.collection.mutable.ArrayBuffer
import _root_.scala.collection.mutable.HashSet
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
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
import lang.psi.api.statements.params.ScClassParameter
import lang.psi.impl.search.ScalaOverridengMemberSearch
import lang.psi.ScalaPsiUtil
import lang.lexer.ScalaTokenTypes
import presentation.java.ClassPresentationUtil
import util.PsiTreeUtil
import lang.psi.api.toplevel.ScNamedElement
import lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScTrait, ScMember, ScObject}
import lang.psi.api.statements._
import lang.psi.types.{Signature}
import extensions.toPsiClassExt

/**
 * User: Alexander Podkhalyuzin
 * Date: 09.11.2008
 */

object ScalaMarkerType {
  private def elemFor(element: PsiElement): PsiElement = element.getNode.getElementType match {
    case ScalaTokenTypes.kTRAIT | ScalaTokenTypes.kCLASS =>
      PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition])
    case ScalaTokenTypes.kTYPE =>
      PsiTreeUtil.getParentOfType(element, classOf[ScTypeAlias])
    case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.kVAL | ScalaTokenTypes.kVAR =>
      PsiTreeUtil.getParentOfType(element, classOf[PsiMember])
    case _ => element
  }

  val OVERRIDING_MEMBER = ScalaMarkerType(new NullableFunction[PsiElement, String] {
    def fun(element: PsiElement): String = {
      val elem = elemFor(element)
      elem match {
        case method: ScFunction => {
          val signatures: Seq[Signature] = method.superSignatures
          //removed assertion, because can be change before adding gutter, so just need to return ""
          if (signatures.length == 0) return ""
          val optionClazz = signatures(0).namedElement.map(ScalaPsiUtil.nameContext(_)).flatMap {
            case member: PsiMember => Option(member.getContainingClass)
            case _ => None
          }
          assert(optionClazz != None)
          val clazz = optionClazz.get
          if (!GutterUtil.isOverrides(element, signatures))
            ScalaBundle.message("implements.method.from.super", clazz.qualifiedName)
          else ScalaBundle.message("overrides.method.from.super", clazz.qualifiedName)
        }
        case _: ScValue | _: ScVariable => {
          val signatures = new ArrayBuffer[Signature]
          val bindings = elem match {case v: ScDeclaredElementsHolder => v.declaredElements case _ => return null}
          for (z <- bindings) signatures ++= ScalaPsiUtil.superValsSignatures(z)
          assert(signatures.length != 0)
          val optionClazz = signatures(0).namedElement.map(ScalaPsiUtil.nameContext(_)).flatMap {
            case member: PsiMember => Option(member.getContainingClass)
            case _ => None
          }
          assert(optionClazz != None)
          val clazz = optionClazz.get
          if (!GutterUtil.isOverrides(element, signatures))
            ScalaBundle.message("implements.val.from.super", clazz.qualifiedName)
          else ScalaBundle.message("overrides.val.from.super", clazz.qualifiedName)
        }
        case x@(_: ScTypeDefinition | _: ScTypeAlias) => {
          val superMembers = ScalaPsiUtil.superTypeMembers(x.asInstanceOf[PsiNamedElement])
          assert(superMembers.length != 0)
          val optionClazz = superMembers(0)
          ScalaBundle.message("overrides.type.from.super", optionClazz.getName)
        }
        case _ => null
      }
    }
  }, new GutterIconNavigationHandler[PsiElement]{
    def navigate(e: MouseEvent, element: PsiElement) {
      val elem = elemFor(element)
      elem match {
        case method: ScFunction => {
          val signatures = method.superSignatures
          val elems = new HashSet[NavigatablePsiElement]
          signatures.foreach {
            case sig =>
              sig.namedElement match {
                case Some(nav: NavigatablePsiElement) => elems += nav
                case _ =>
              }
          }
          elems.size match {
            case 0 =>
            case 1 => {
              val elem = elems.iterator.next()
              if (elem.canNavigate) elem.navigate(true)
            }
            case _ => {
              val gotoDeclarationPopup = NavigationUtil.getPsiElementPopup(elems.toArray, new ScCellRenderer,
              ScalaBundle.message("goto.override.method.declaration"))
              gotoDeclarationPopup.show(new RelativePoint(e))
            }
          }
        }
        case _: ScValue | _: ScVariable => {
          val signatures = new ArrayBuffer[Signature]
          val bindings = elem match {case v: ScDeclaredElementsHolder => v.declaredElements case _ => return}
          for (z <- bindings) signatures ++= ScalaPsiUtil.superValsSignatures(z)
          val elems = new HashSet[NavigatablePsiElement]
          signatures.foreach {
            case sig =>
              sig.namedElement match {
                case Some(n: NavigatablePsiElement) => elems += n
                case _ =>
              }
          }
          elems.size match {
            case 0 =>
            case 1 => {
              val elem = elems.iterator.next()
              if (elem.canNavigate) elem.navigate(true)
            }
            case _ => {
              val gotoDeclarationPopup = NavigationUtil.getPsiElementPopup(elems.toArray, new ScCellRenderer,
              ScalaBundle.message("goto.override.val.declaration"))
              gotoDeclarationPopup.show(new RelativePoint(e))
            }
          }
        }
        case x @(_: ScTypeDefinition | _: ScTypeAlias) => {
          val elems = ScalaPsiUtil.superTypeMembers(x.asInstanceOf[PsiNamedElement])

          elems.toSeq match {
            case Seq() =>
            case Seq(x: NavigatablePsiElement) => {
              if (x.canNavigate) x.navigate(true)
            }
            case _ => {
              val gotoDeclarationPopup = NavigationUtil.getPsiElementPopup(elems.toArray, new ScCellRenderer,
              ScalaBundle.message("goto.override.type.declaration"))
              gotoDeclarationPopup.show(new RelativePoint(e))
            }
          }
        }
        case _ =>
      }
    }
  })

  val OVERRIDDEN_MEMBER = ScalaMarkerType(new NullableFunction[PsiElement, String]{
    def fun(element: PsiElement): String = {
      var elem = element
      element.getNode.getElementType match {
        case  ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.kVAL | ScalaTokenTypes.kVAR => {
          elem = PsiTreeUtil.getParentOfType(element, classOf[PsiMember])
        }
        case _ =>
      }
      elem match {
        case _: PsiMember =>
          if (GutterUtil.isAbstract(element)) ScalaBundle.message("has.implementations")
          else ScalaBundle.message("is.overriden.by")
        case _ => null
      }
    }
  }, new GutterIconNavigationHandler[PsiElement] {
    def navigate(e: MouseEvent, element: PsiElement) {
      var elem = element
      element.getNode.getElementType match {
        case  ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.kVAL | ScalaTokenTypes.kVAR => {
          elem = PsiTreeUtil.getParentOfType(element, classOf[PsiMember])
        }
        case _ =>
      }
      val members = elem match {
        case memb: PsiNamedElement => Array[PsiNamedElement](memb)
        case d: ScDeclaredElementsHolder => d.declaredElements.toArray
        case _ => return
      }
      val overrides = new ArrayBuffer[PsiNamedElement]
      for (member <- members) overrides ++= ScalaOverridengMemberSearch.search(member)
      if (overrides.length == 0) return
      val title = if (GutterUtil.isAbstract(element)) ScalaBundle.
              message("navigation.title.implementation.member", members(0).getName, "" + overrides.length)
                  else ScalaBundle.message("navigation.title.overrider.member", members(0).getName, "" + overrides.length)
      val renderer = new ScCellRenderer
      Arrays.sort(overrides.map(_.asInstanceOf[PsiElement]).toArray, renderer.getComparator)
      PsiElementListNavigator.openTargets(e, overrides.map(_.asInstanceOf[NavigatablePsiElement]).toArray, title, renderer)
    }
  })

  val SUBCLASSED_CLASS = ScalaMarkerType(new NullableFunction[PsiElement, String]{
    def fun(element: PsiElement): String = {
      var elem = element
      if (element.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER) {
        elem = PsiTreeUtil.getParentOfType(element, classOf[ScNamedElement])
      }
      if (!elem.isInstanceOf[PsiClass]) return null
      elem match {
        case _: ScTrait => ScalaBundle.message("trait.has.implementations")
        case _: ScObject => ScalaBundle.message("object.has.subclasses")
        case _ => ScalaBundle.message("class.has.subclasses")
      }
    }
  }, new GutterIconNavigationHandler[PsiElement] {
    def navigate(e: MouseEvent, element: PsiElement) {
      var elem = element
      if (element.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER) {
        elem = PsiTreeUtil.getParentOfType(element, classOf[ScNamedElement])
      }
      val clazz = elem match {
        case x: PsiClass => x
        case _ => return
      }
      val inheritors = ClassInheritorsSearch.search(clazz, clazz.getUseScope, true).toArray(PsiClass.EMPTY_ARRAY)
      if (inheritors.length == 0) return
      val title = clazz match {
        case _: ScTrait => ScalaBundle.message("goto.implementation.chooser.title", clazz.getName, "" + inheritors.length)
        case _ => ScalaBundle.message("navigation.title.subclass", clazz.getName, "" + inheritors.length)
      }
      val renderer = new PsiClassListCellRenderer
      Arrays.sort(inheritors, renderer.getComparator)
      PsiElementListNavigator.openTargets(e, inheritors.map(_.asInstanceOf[NavigatablePsiElement]), title, renderer)
    }
  })

  class ScCellRenderer extends PsiElementListCellRenderer[PsiElement] {
    def getElementText(element: PsiElement): String = {
      def defaultPresentation: String = {
        element.getText.substring(0, math.min(element.getText.length, 20))
      }

      element match {
        case method: PsiMethod if method.getContainingClass != null => {
          val presentation = method.getContainingClass.getPresentation
          if (presentation != null)
            presentation.getPresentableText + " " + presentation.getLocationString
          else {
            ClassPresentationUtil.getNameForClass(method.getContainingClass, false)
          }
        }
        case xlass: PsiClass => {
          val presentation = xlass.getPresentation
          presentation.getPresentableText + " " + presentation.getLocationString
        }
        case x: PsiNamedElement if ScalaPsiUtil.nameContext(x).isInstanceOf[ScMember] => {
          val containing = ScalaPsiUtil.nameContext(x).asInstanceOf[ScMember].getContainingClass
          if (containing == null) defaultPresentation
          else  {
            val presentation = containing.getPresentation
            presentation.getPresentableText + " " + presentation.getLocationString
          }
        }
        case x: ScClassParameter => {
          val presentation = x.getPresentation
          presentation.getPresentableText + " " + presentation.getLocationString
        }
        case x: PsiNamedElement => x.getName
        case _ => defaultPresentation
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

case class ScalaMarkerType(fun: com.intellij.util.Function[PsiElement,String], handler: GutterIconNavigationHandler[PsiElement])