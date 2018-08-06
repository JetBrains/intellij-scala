package org.jetbrains.plugins.scala
package annotator
package gutter

import java.awt.event.MouseEvent
import java.util

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator
import com.intellij.ide.util.{PsiClassListCellRenderer, PsiElementListCellRenderer}
import com.intellij.psi._
import com.intellij.psi.presentation.java.ClassPresentationUtil
import com.intellij.psi.search.searches.ClassInheritorsSearch
import javax.swing.{Icon, ListCellRenderer}
import org.jetbrains.plugins.scala.annotator.gutter.GutterUtil.namedParent
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.search.ScalaOverridingMemberSearcher
import org.jetbrains.plugins.scala.lang.psi.types.Signature
import org.jetbrains.plugins.scala.util.SAMUtil

/**
 * User: Alexander Podkhalyuzin
 * Date: 09.11.2008
 */
object ScalaMarkerType {
  private[this] def extractClassName(sigs: Seq[Signature]): Option[String] =
    sigs.headOption.map(_.namedElement).collect { case ContainingClass(aClass) => aClass.qualifiedName }

  private[this] def sigToNavigatableElement(s: Signature): Option[NavigatablePsiElement] = s.namedElement match {
    case ne: NavigatablePsiElement => Option(ne)
    case _                         => None
  }

  private[this] def navigateToSuperMember(
    event:           MouseEvent,
    members:         Array[NavigatablePsiElement],
    title:           String,
    findUsagesTitle: String,
    renderer:        ListCellRenderer[_] = new ScCellRenderer
  ): Unit = PsiElementListNavigator.openTargets(event, members, title, findUsagesTitle, renderer)

  private[this] def navigateToSuperMethod(
    event:       MouseEvent,
    method:      PsiMethod,
    includeSelf: Boolean
  ): Unit = {
    val superMethods = (method match {
      case fn: ScFunction =>
        val sigs = fn.superSignaturesIncludingSelfType
        sigs.flatMap(sigToNavigatableElement).toArray
      case _ => method.findSuperMethods(false).map(e => e: NavigatablePsiElement)
    }) ++ (if (includeSelf) Array(method) else NavigatablePsiElement.EMPTY_NAVIGATABLE_ELEMENT_ARRAY)

    val title           = ScalaBundle.message("navigation.title.super.methods", method.getName)
    val findUsagesTitle = ScalaBundle.message("navigation.findUsages.title.super.methods", method.getName)
    navigateToSuperMember(event, superMethods, title, findUsagesTitle)
  }

  val overridingMember: ScalaMarkerType = ScalaMarkerType(
    element =>
      namedParent(element)
        .flatMap {
          case method: ScFunction =>
            val signatures = method.superSignaturesIncludingSelfType
            val maybeClass = extractClassName(signatures)

            val key =
              if (GutterUtil.isOverrides(element, signatures)) "overrides.method.from.super"
              else "implements.method.from.super"

            maybeClass.map(ScalaBundle.message(key, _))
          case param: ScClassParameter =>
            val signatures = ScalaPsiUtil.superValsSignatures(param, withSelfType = true)
            val maybeClass = extractClassName(signatures)
            val key =
              if (GutterUtil.isOverrides(element, signatures)) "overrides.val.from.super"
              else "implements.val.from.super"

            maybeClass.map(ScalaBundle.message(key, _))
          case v: ScValueOrVariable =>
            val bindings   = v.declaredElements.filter(_.name == element.getText)
            val signatures = bindings.flatMap(ScalaPsiUtil.superValsSignatures(_, withSelfType = true))
            val maybeClass = extractClassName(signatures)

            val key =
              if (GutterUtil.isOverrides(element, signatures)) "overrides.val.from.super"
              else "implements.val.from.super"

            maybeClass.map(ScalaBundle.message(key, _))
          case ta: ScTypeAlias =>
            val superMembers = ScalaPsiUtil.superTypeMembers(ta, withSelfType = true)
            val maybeClass   = superMembers.headOption.collect { case ContainingClass(aClass) => aClass }
            maybeClass.map(cls => ScalaBundle.message("overrides.type.from.super", cls.name))
          case _ => None
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
          val bindings        = v.declaredElements.filter(_.name == element.getText)
          val signatures      = bindings.flatMap(ScalaPsiUtil.superValsSignatures(_, withSelfType = true))
          val superMembers    = signatures.flatMap(sigToNavigatableElement).toArray
          val title           = ScalaBundle.message("navigation.title.super.vals", element.getText)
          val findUsagesTitle = ScalaBundle.message("navigation.findUsages.title.super.vals", element.getText)
          navigateToSuperMember(event, superMembers, title, findUsagesTitle)
        case ta: ScTypeAlias =>
          val superElements = ScalaPsiUtil.superTypeMembers(ta, withSelfType = true)
          val navigatable: Array[NavigatablePsiElement] =
            superElements.collect { case ne: NavigatablePsiElement => ne }.toArray
          val title           = ScalaBundle.message("navigation.title.super.types", ta.getName)
          val findUsagesTitle = ScalaBundle.message("navigation.findUsages.title.super.types", ta.getName)
          navigateToSuperMember(event, navigatable, title, findUsagesTitle)
    }
  )

  val overriddenMember = ScalaMarkerType(
    element =>
      namedParent(element).collect {
        case _: ScMember =>
          if (GutterUtil.isAbstract(element)) ScalaBundle.message("has.implementations")
          else ScalaBundle.message("is.overridden.by")
      }.orNull,
    (event, element) =>
      namedParent(element).collect {
        case member: ScMember =>
          val namedElement = member match {
            case memb: ScNamedElement => Seq(memb)
            case _                    => Seq.empty
          }

          val overrides = namedElement.flatMap(ScalaOverridingMemberSearcher.search(_, withSelfType = true))

          if (overrides.nonEmpty) {
            val name = namedElement.headOption.fold("")(_.name)

            val keyBuilder = (isFindUsages: Boolean, isAbstract: Boolean) => {
              val windowType = if (isFindUsages) "findUsages." else ""
              val targetType = if (isAbstract) "implementing"  else "overriding"
              s"navigation.${windowType}title.$targetType.member"
            }

            val isAbstract      = GutterUtil.isAbstract(member)
            val title           = ScalaBundle.message(keyBuilder(false, isAbstract), name, overrides.length.toString)
            val findUsagesTitle = ScalaBundle.message(keyBuilder(true, isAbstract), name)

            val renderer = new ScCellRenderer
            util.Arrays.sort(overrides.map(e => e: PsiElement).toArray, renderer.getComparator)
            PsiElementListNavigator.openTargets(
              event,
              overrides.map(_.asInstanceOf[NavigatablePsiElement]).toArray,
              title,
              findUsagesTitle,
              renderer
            )
          }
    }
  )

  val subclassedClass = ScalaMarkerType(
    element =>
      element.parent.collect {
        case _: ScTrait => ScalaBundle.message("trait.has.implementations")
        case _          => ScalaBundle.message("class.has.subclasses")
      }.orNull,
    (event, element) =>
      element.parent.collect {
        case aClass: PsiClass =>
          val inheritors = ClassInheritorsSearch.search(aClass, aClass.getUseScope, true).toArray(PsiClass.EMPTY_ARRAY)
          if (inheritors.nonEmpty) {
            val keyBuilder = (isFindUsages: Boolean, isTrait: Boolean) => {
              val windowType = if (isFindUsages) "findUsages." else ""
              val targetType = if (isTrait) "trait"            else "class"
              s"navigation.${windowType}title.inheritors.$targetType"
            }

            val isTrait         = aClass.isInstanceOf[ScTrait]
            val title           = ScalaBundle.message(keyBuilder(false, isTrait), aClass.name, inheritors.length.toString)
            val findUsagesTitle = ScalaBundle.message(keyBuilder(true, isTrait), aClass.name)

            val renderer = new PsiClassListCellRenderer
            util.Arrays.sort(inheritors, renderer.getComparator)
            PsiElementListNavigator.openTargets(
              event,
              inheritors.map(_.asInstanceOf[NavigatablePsiElement]),
              title,
              findUsagesTitle,
              renderer
            )
          }
    }
  )

  def samTypeImplementation(aClass: PsiClass): ScalaMarkerType = ScalaMarkerType(
    _ => ScalaBundle.message("implements.method.from.super", aClass.qualifiedName),
    (event, _) => SAMUtil.singleAbstractMethodOf(aClass).foreach(navigateToSuperMethod(event, _, includeSelf = true))
  )

  class ScCellRenderer extends PsiElementListCellRenderer[PsiElement] {
    def getElementText(element: PsiElement): String = {
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

    def getContainerText(psiElement: PsiElement, s: String): Null = null

    def getIconFlags: Int = 0

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
