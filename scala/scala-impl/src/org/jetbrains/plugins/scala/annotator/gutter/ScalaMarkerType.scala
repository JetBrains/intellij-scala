package org.jetbrains.plugins.scala
package annotator
package gutter

import java.awt.event.MouseEvent
import java.util

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.impl.{GutterTooltipHelper, PsiElementListNavigator}
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.ide.util.{PsiClassListCellRenderer, PsiElementListCellRenderer}
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi._
import com.intellij.psi.presentation.java.ClassPresentationUtil
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.ui.ColorUtil.toHex
import com.intellij.ui.JBColor
import javax.swing.{Icon, ListCellRenderer}
import org.jetbrains.plugins.scala.annotator.gutter.GutterUtil.namedParent
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.search.ScalaOverridingMemberSearcher
import org.jetbrains.plugins.scala.lang.psi.types.TermSignature
import org.jetbrains.plugins.scala.util.SAMUtil

import scala.collection.JavaConverters.seqAsJavaListConverter

/**
 * User: Alexander Podkhalyuzin
 * Date: 09.11.2008
 */
object ScalaMarkerType {
  private[this] def sigToNavigatableElement(s: TermSignature): Option[NavigatablePsiElement] = s.namedElement match {
    case ne: NavigatablePsiElement => Option(ne)
    case _                         => None
  }

  private[this] def navigateToSuperMember[T <: NavigatablePsiElement](
    event:           MouseEvent,
    members:         Array[T],
    title:           String,
    findUsagesTitle: String,
    renderer:        ListCellRenderer[T] = newCellRenderer.asInstanceOf[ListCellRenderer[T]]
  ): Unit = PsiElementListNavigator.openTargets(event, members, title, findUsagesTitle, renderer)

  private[this] def navigateToSuperMethod(
    event:       MouseEvent,
    method:      PsiMethod,
    includeSelf: Boolean
  ): Unit = {
    val superMethods = superMethodsOf(method, includeSelf)
    val title           = ScalaBundle.message("navigation.title.super.methods", method.name)
    val findUsagesTitle = ScalaBundle.message("navigation.findUsages.title.super.methods", method.name)
    navigateToSuperMember(event, superMethods, title, findUsagesTitle)
  }

  private def superMethodsOf(method: PsiMethod, includeSelf: Boolean): Array[NavigatablePsiElement] = {
    val superMethods = (if (includeSelf) Array(method) else NavigatablePsiElement.EMPTY_NAVIGATABLE_ELEMENT_ARRAY) ++ (method match {
      case fn: ScFunction =>
        val sigs = fn.superSignaturesIncludingSelfType
        sigs.flatMap(sigToNavigatableElement).toArray[NavigatablePsiElement]
      case _ => method.findSuperMethods(false).map(e => e: NavigatablePsiElement)
    })
    superMethods
  }

  def findOverrides(member: ScMember, deep: Boolean): Seq[PsiNamedElement] = {

    val namedElems: Seq[ScNamedElement] = member match {
      case d: ScDeclaredElementsHolder => d.declaredElements.filterBy[ScNamedElement]
      case param: ScClassParameter => Seq(param)
      case ta: ScTypeAlias => Seq(ta)
      case _ => Seq.empty
    }

    namedElems.flatMap(ScalaOverridingMemberSearcher.search(_, deep = deep, withSelfType = true))
  }

  val overridingMember: ScalaMarkerType = ScalaMarkerType(
    element =>
      namedParent(element)
        .collect {
          case method: ScFunction =>
            val signatures = method.superSignaturesIncludingSelfType
            (signatures.map(_.namedElement),
              if (GutterUtil.isOverrides(element, signatures)) ScalaBundle.message("overrides.method.from.super")
              else ScalaBundle.message("implements.method.from.super"))

          case param: ScClassParameter =>
            val signatures = ScalaPsiUtil.superValsSignatures(param, withSelfType = true)
            (signatures.map(_.namedElement),
              if (GutterUtil.isOverrides(element, signatures)) ScalaBundle.message("overrides.val.from.super")
              else ScalaBundle.message("implements.val.from.super"))

          case v: ScValueOrVariable =>
            val bindings   = v.declaredElements.filter(e => element.textMatches(e.name))
            val signatures = bindings.flatMap(ScalaPsiUtil.superValsSignatures(_, withSelfType = true))
            (signatures.map(_.namedElement),
              if (GutterUtil.isOverrides(element, signatures)) ScalaBundle.message("overrides.val.from.super")
              else ScalaBundle.message("implements.val.from.super"))

          case ta: ScTypeAlias =>
            val superMembers = ScalaPsiUtil.superTypeMembers(ta, withSelfType = true)
            (superMembers, ScalaBundle.message("overrides.type.from.super"))
        }
        .map { case (namedElements, prefix) =>
          GutterTooltipHelper.getTooltipText(namedElements.asJava,
            (e: PsiElement) => (if (e == namedElements.head) prefix else elementDivider + prefix) + " ",
            (_: PsiElement) => true,
            null)
        }
        .orNull,
    (event, element) =>
      namedParent(element).collect {
        case method: ScFunction => navigateToSuperMethod(event, method, includeSelf = false)
        case param: ScClassParameter =>
          val signatures      = ScalaPsiUtil.superValsSignatures(param, withSelfType = true)
          val superMembers    = signatures.flatMap(sigToNavigatableElement).toArray
          val title           = ScalaBundle.message("navigation.title.super.vals", element.getText)
          val findUsagesTitle = ScalaBundle.message("navigation.findUsages.title.super.vals", element.getText)
          navigateToSuperMember(event, superMembers, title, findUsagesTitle)
        case v: ScValueOrVariable =>
          val bindings        = v.declaredElements.filter(e => element.textMatches(e.name))
          val signatures      = bindings.flatMap(ScalaPsiUtil.superValsSignatures(_, withSelfType = true))
          val superMembers    = signatures.flatMap(sigToNavigatableElement).toArray
          val title           = ScalaBundle.message("navigation.title.super.vals", element.getText)
          val findUsagesTitle = ScalaBundle.message("navigation.findUsages.title.super.vals", element.getText)
          navigateToSuperMember(event, superMembers, title, findUsagesTitle)
        case ta: ScTypeAlias =>
          val superElements = ScalaPsiUtil.superTypeMembers(ta, withSelfType = true)
          val navigatable: Array[NavigatablePsiElement] =
            superElements.collect { case ne: NavigatablePsiElement => ne }.toArray
          val title           = ScalaBundle.message("navigation.title.super.types", ta.name)
          val findUsagesTitle = ScalaBundle.message("navigation.findUsages.title.super.types", ta.name)
          navigateToSuperMember(event, navigatable, title, findUsagesTitle)
    }
  )

  val overriddenMember: ScalaMarkerType = ScalaMarkerType(
    element =>
      namedParent(element).collect {
        case _: ScMember =>
          if (GutterUtil.isAbstract(element)) ScalaBundle.message("has.implementations")
          else ScalaBundle.message("is.overridden.by")
      }.orNull,
    (event, element) =>
      namedParent(element).collect {
        case member: ScMember =>

          val overrides = findOverrides(member, deep = true)

          if (overrides.nonEmpty) {
            val name = overrides.headOption.fold("")(_.name)

            val (title, findUsagesTitle) =
              if (GutterUtil.isAbstract(member)) {
                ScalaBundle.message("navigation.title.implementing.member", name, overrides.length.toString) ->
                  ScalaBundle.message("navigation.findUsages.title.implementing.member", name)
              } else {
                ScalaBundle.message("navigation.title.overriding.member", name, overrides.length.toString) ->
                  ScalaBundle.message("navigation.findUsages.title.overriding.member", name)
              }

            val renderer = newCellRenderer
            util.Arrays.sort(overrides.map(e => e: PsiElement).toArray, renderer.getComparator)
            PsiElementListNavigator.openTargets(
              event,
              overrides.map(_.asInstanceOf[NavigatablePsiElement]).toArray,
              title,
              findUsagesTitle,
              renderer.asInstanceOf[ListCellRenderer[NavigatablePsiElement]]
            )
          }
    }
  )

  def newCellRenderer: PsiElementListCellRenderer[PsiElement] = new ScCellRenderer

  val subclassedClass: ScalaMarkerType = ScalaMarkerType(
    element =>
      element.parent.collect {
        case aClass: PsiClass =>
          val inheritors = ClassInheritorsSearch.search(aClass, aClass.getUseScope, true).toArray(PsiClass.EMPTY_ARRAY).toSeq.map(nameIdOf)
          val prefix = if (aClass.is[ScTrait]) ScalaBundle.message("trait.has.implementations") else ScalaBundle.message("class.has.subclasses")
          GutterTooltipHelper.getTooltipText(inheritors.asJava,
            (e: PsiElement) => (if (e == inheritors.head) prefix else elementDivider + prefix) + " ",
            (_: PsiElement) => false,
            null)
      }.orNull,
    (event, element) =>
      element.parent.collect {
        case aClass: PsiClass =>
          val inheritors = ClassInheritorsSearch.search(aClass, aClass.getUseScope, true).toArray(PsiClass.EMPTY_ARRAY)
          if (inheritors.nonEmpty) {
            val cname = aClass.name
            val (title, findUsagesTitle) =
              if (aClass.isInstanceOf[ScTrait]) {
                ScalaBundle.message("navigation.title.inheritors.trait", cname, inheritors.length.toString) ->
                  ScalaBundle.message("navigation.findUsages.title.inheritors.trait", cname)
              } else {
                ScalaBundle.message("navigation.title.inheritors.class", cname, inheritors.length.toString) ->
                  ScalaBundle.message("navigation.findUsages.title.inheritors.class", cname)
              }

            val renderer = new PsiClassListCellRenderer
            util.Arrays.sort(inheritors, renderer.getComparator)
            PsiElementListNavigator.openTargets(
              event,
              inheritors,
              title,
              findUsagesTitle,
              renderer.asInstanceOf[ListCellRenderer[PsiClass]]
            )
          }
    }
  )

  def samTypeImplementation(aClass: PsiClass): ScalaMarkerType = {
    val tooltipProvider = (_: PsiElement) => {
      val psiElements = SAMUtil.singleAbstractMethod(aClass).toSeq.flatMap(superMethodsOf(_, includeSelf = true)).map(nameIdOf)
      val prefix = ScalaBundle.message("implements.method.from.super")
      GutterTooltipHelper.getTooltipText(psiElements.asJava,
        (e: PsiElement) => (if (e == psiElements.head) prefix else elementDivider + prefix) + " ",
        (_: PsiElement) => true,
        null)
    }
    ScalaMarkerType(tooltipProvider, (event, _) => SAMUtil.singleAbstractMethod(aClass).foreach(navigateToSuperMethod(event, _, includeSelf = true)))
  }

  private def nameIdOf(element: PsiElement): PsiElement = element match {
    case namedElement: ScNamedElement => namedElement.nameId
    case identifierOwner: PsiNameIdentifierOwner => identifierOwner.getNameIdentifier
    case element => element
  }

  // com.intellij.codeInsight.daemon.impl.GutterTooltipHelper.getElementDivider
  private def elementDivider: String = {
    val separatorColor = toHex(JBColor.namedColor("GutterTooltip.lineSeparatorColor", HintUtil.INFORMATION_BORDER_COLOR))
    s"</p><p style='margin-top:2pt;border-top:thin solid #$separatorColor;'>"
  }

  private class ScCellRenderer extends PsiElementListCellRenderer[PsiElement] {

    override def getElementText(element: PsiElement): String = {
      def defaultPresentation: String =
        element.getText.substring(0, math.min(element.getText.length, 20))

      element match {
        case method: PsiMethod if method.containingClass != null =>
          val presentation = method.containingClass.getPresentation
          if (presentation != null)
            presentation.getPresentableText + " " + presentation.getLocationString
          else {
            ClassPresentationUtil.getNameForClass(method.containingClass, false)
          }
        case xlass: PsiClass =>
          val presentation = xlass.getPresentation
          presentation.getPresentableText + " " + presentation.getLocationString
        case x: PsiNamedElement if ScalaPsiUtil.nameContext(x).isInstanceOf[ScMember] =>
          val containing = ScalaPsiUtil.nameContext(x).asInstanceOf[ScMember].containingClass
          if (containing == null) defaultPresentation
          else {
            val presentation = containing.getPresentation
            presentation.getPresentableText + " " + presentation.getLocationString
          }
        case x: ScClassParameter =>
          val presentation = x.getPresentation
          presentation.getPresentableText + " " + presentation.getLocationString
        case x: PsiNamedElement => x.name
        case _                  => defaultPresentation
      }
    }

    override def getContainerText(psiElement: PsiElement, s: String): Null = null

    override def getIconFlags: Int = 0

    override def getIcon(element: PsiElement): Icon =
      element match {
        case _: PsiMethod => super.getIcon(element)
        case x: PsiNamedElement if ScalaPsiUtil.nameContext(x) != null =>
          ScalaPsiUtil.nameContext(x).getIcon(getIconFlags)
        case _ => super.getIcon(element)
      }
  }
}

case class ScalaMarkerType(
  tooltipProvider:   com.intellij.util.Function[PsiElement, String],
  navigationHandler: GutterIconNavigationHandler[PsiElement]
)
