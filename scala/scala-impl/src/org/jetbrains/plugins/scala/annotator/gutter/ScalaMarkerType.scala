package org.jetbrains.plugins.scala
package annotator
package gutter

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.impl.{GutterTooltipHelper, PsiElementListNavigator}
import com.intellij.ide.util.{PsiClassListCellRenderer, PsiElementListCellRenderer}
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi._
import com.intellij.psi.presentation.java.ClassPresentationUtil
import com.intellij.psi.search.searches.ClassInheritorsSearch
import org.jetbrains.annotations.Nls
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

import java.awt.event.MouseEvent
import java.util
import java.util.Collections.emptyList
import javax.swing.{Icon, ListCellRenderer}
import scala.jdk.CollectionConverters._

object ScalaMarkerType {
  private[this] def sigToNavigatableElement(s: TermSignature): Option[NavigatablePsiElement] = s.namedElement match {
    case ne: NavigatablePsiElement => Option(ne)
    case _                         => None
  }

  private[this] def navigateToSuperMember[T <: NavigatablePsiElement](
    event:                MouseEvent,
    members:              Array[T],
    @Nls title:           String,
    @Nls findUsagesTitle: String,
    renderer:             ListCellRenderer[T] = newCellRenderer.asInstanceOf[ListCellRenderer[T]]
  ): Unit = PsiElementListNavigator.openTargets(event, members, title, findUsagesTitle, renderer)

  private[this] def navigateToSuperType[T <: NavigatablePsiElement](event: MouseEvent, members: Array[T], name: String): Unit = {
    val title           = ScalaBundle.message("navigation.title.super.types", name)
    val findUsagesTitle = ScalaBundle.message("navigation.findUsages.title.super.types", name)
    navigateToSuperMember(event, members, title, findUsagesTitle)
  }

  private[this] def navigateToSuperMember[T <: NavigatablePsiElement](event: MouseEvent, members: Array[T], name: String): Unit = {
    val title           = ScalaBundle.message("navigation.title.super.members", name)
    val findUsagesTitle = ScalaBundle.message("navigation.findUsages.title.super.members", name)
    navigateToSuperMember(event, members, title, findUsagesTitle)
  }

  private[this] def navigateToSuperMethod(
    event:       MouseEvent,
    method:      PsiMethod,
    includeSelf: Boolean
  ): Unit = {
    val superMethods = superMethodsOf(method, includeSelf)
    navigateToSuperMember(event, superMethods, method.name)
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

    val namedElems = member match {
      case d: ScDeclaredElementsHolder => d.declaredElements.filterByType[ScNamedElement]
      case param: ScClassParameter => Seq(param)
      case ta: ScTypeAlias => Seq(ta)
      case _ => Seq.empty
    }

    namedElems.flatMap(ScalaOverridingMemberSearcher.search(_, deep = deep, withSelfType = true))
  }

  val overridingMember: ScalaMarkerType = ScalaMarkerType(
    element => {
      namedParent(element)
        .collect {
          case method: ScFunction =>
            val supers = method.superSignaturesIncludingSelfType.map(_.namedElement)
            (method, supers)
          case param: ScClassParameter =>
            val supers = ScalaPsiUtil.superValsSignatures(param, withSelfType = true)
              .map(_.namedElement)
            (param, supers)
          case v: ScValueOrVariable =>
            val bindings   = v.declaredElements.filter(e => element.textMatches(e.name))
            val supers = bindings
              .flatMap(ScalaPsiUtil.superValsSignatures(_, withSelfType = true))
              .map(_.namedElement)
            (v, supers)
          case ta: ScTypeAlias =>
            (ta, ScalaPsiUtil.superTypeMembers(ta, withSelfType = true))
        }
        .map { case (member, namedElements) =>
          val prefix = overridesImplementsPrefix(member, namedElements)
          val shownElements =
            if (namedElements.size > maxNumberOfElements) emptyList else namedElements.asJava
          GutterTooltipHelper.getTooltipText(shownElements,
            prefix,
            true, //skip member, show only containing class
            IdeActions.ACTION_GOTO_SUPER)
        }
        .orNull
    },
    (event, element) =>
      namedParent(element).collect {
        case method: ScFunction => navigateToSuperMethod(event, method, includeSelf = false)
        case param: ScClassParameter =>
          val signatures      = ScalaPsiUtil.superValsSignatures(param, withSelfType = true)
          val superMembers    = signatures.flatMap(sigToNavigatableElement).toArray
          navigateToSuperMember(event, superMembers, param.name)
        case v: ScValueOrVariable =>
          val bindings        = v.declaredElements.filter(e => element.textMatches(e.name))
          val signatures      = bindings.flatMap(ScalaPsiUtil.superValsSignatures(_, withSelfType = true))
          val superMembers    = signatures.flatMap(sigToNavigatableElement).toArray
          navigateToSuperMember(event, superMembers, element.getText)
        case ta: ScTypeAlias =>
          val superElements = ScalaPsiUtil.superTypeMembers(ta, withSelfType = true)
          val navigatables = superElements.filterByType[NavigatablePsiElement].toArray
          navigateToSuperType(event, navigatables, ta.name)
    }
  )

  val overriddenMember: ScalaMarkerType = ScalaMarkerType(
    element =>
      namedParent(element).collect {
        case m: ScMember =>
          if (GutterUtil.isAbstract(m)) ScalaBundle.message("has.implementations")
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
          val inheritors = ClassInheritorsSearch.search(aClass, aClass.getUseScope, true).toArray(PsiClass.EMPTY_ARRAY).toSeq
          val isTooMany = inheritors.size > maxNumberOfElements
          val prefix = aClass match {
            case _: ScTrait =>
              if (isTooMany) ScalaBundle.message("trait.has.several.implementations", inheritors.size)
              else           ScalaBundle.message("trait.has.implementations")
            case _ =>
              if (isTooMany) ScalaBundle.message("class.has.several.subclasses", inheritors.size)
              else           ScalaBundle.message("class.has.subclasses")
          }
          val shownInheritors = if (isTooMany) emptyList else inheritors.asJava

          GutterTooltipHelper.getTooltipText(shownInheritors,
              prefix,
              false, //do not skip inheritor itself
              IdeActions.ACTION_GOTO_IMPLEMENTATION)

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
      val psiElements = SAMUtil.singleAbstractMethod(aClass).toSeq.flatMap(superMethodsOf(_, includeSelf = true))
      val prefix = ScalaBundle.message("implements.member.prefix")
      GutterTooltipHelper.getTooltipText(psiElements.asJava,
        prefix,
        false,
        IdeActions.ACTION_GOTO_SUPER)
    }
    ScalaMarkerType(tooltipProvider, (event, _) => SAMUtil.singleAbstractMethod(aClass).foreach(navigateToSuperMethod(event, _, includeSelf = true)))
  }

  private val maxNumberOfElements = 5

  private def overridesImplementsPrefix(member: ScMember, supers: Seq[PsiNamedElement]): String = {
    val isTooMany = supers.size > maxNumberOfElements
    val isOverrides = GutterUtil.isOverrides(member, supers)
    member match {
      case _: ScFunction | _: ScValueOrVariable | _: ScClassParameter =>
        (isOverrides, isTooMany) match {
          case (true, true)   => ScalaBundle.message("overrides.member.from.several.classes", supers.size)
          case (true, false)  => ScalaBundle.message("overrides.member.from.prefix")
          case (false, true)  => ScalaBundle.message("implements.member.from.several.classes", supers.size)
          case (false, false) => ScalaBundle.message("implements.member.from.prefix")
        }
      case _: ScTypeAlias =>
        if (isTooMany) ScalaBundle.message("overrides.type.from.super.several.classes", supers.size)
        else           ScalaBundle.message("overrides.type.prefix")
    }
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
