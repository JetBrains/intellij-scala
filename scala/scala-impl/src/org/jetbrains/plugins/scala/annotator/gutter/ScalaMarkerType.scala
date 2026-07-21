package org.jetbrains.plugins.scala.annotator.gutter

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.impl.GutterTooltipHelper
import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.psi._
import com.intellij.psi.presentation.java.ClassPresentationUtil
import com.intellij.psi.search.searches.ClassInheritorsSearch
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.gutter.GutterUtil.namedParent
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.search.ScalaExportedMemberUtil
import org.jetbrains.plugins.scala.lang.psi.impl.search.ScalaOverridingMemberSearcher
import org.jetbrains.plugins.scala.lang.psi.types.TermSignature
import org.jetbrains.plugins.scala.util.SAMUtil

import java.awt.event.MouseEvent
import java.util
import java.util.Collections.emptyList
import javax.swing.{Icon, JComponent}
import scala.jdk.CollectionConverters._

object ScalaMarkerType {

  private def sigToNavigatableElement(s: TermSignature): Option[NavigatablePsiElement] = s.namedElement match {
    case ne: NavigatablePsiElement => Option(ne)
    case _ => None
  }

  private def navigateToSuperMethod(
    event: MouseEvent,
    method: PsiMethod,
    project: Project,
    includeSelf: Boolean
  ): Unit = {
    val superMethods = superMethodsOf(method, includeSelf)
    ScalaNavigationUtils.navigateToSuperMember(event, superMethods, project, method.name)
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
      case param: ScClassParameter     => Seq(param)
      case ta: ScTypeAlias             => Seq(ta)
      case _                           => Seq.empty
    }

    namedElems.flatMap(ScalaOverridingMemberSearcher.search(_, deep = deep, withSelfType = true))
  }

  private def overridingTooltip(member: PsiElement, namedElements: Seq[PsiNamedElement]): String = {
    val prefix = overridesImplementsPrefix(member, namedElements)
    val shownElements =
      if (namedElements.size > maxNumberOfElements) emptyList
      else namedElements.asJava

    //skip the first member, show only containing class
    val skipFirstMember = true
    GutterTooltipHelper.getTooltipText(shownElements,
      prefix,
      skipFirstMember,
      IdeActions.ACTION_GOTO_SUPER
    )
  }

  val overridingMember: ScalaMarkerType = ScalaMarkerType(
    tooltipProvider = element => {
      val ordinaryTooltip = namedParent(element)
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
        .map { case (member, namedElements) => overridingTooltip(member, namedElements) }

      val tooltip = ordinaryTooltip.orElse(tooltipForExport(element))
      tooltip.orNull
    },
    navigationHandler = (event, element) =>
      namedParent(element).collect {
        case method: ScFunction =>
          navigateToSuperMethod(event, method, method.getProject, includeSelf = false)
        case param: ScClassParameter =>
          val signatures      = ScalaPsiUtil.superValsSignatures(param, withSelfType = true)
          val superMembers    = signatures.flatMap(sigToNavigatableElement).toArray
          ScalaNavigationUtils.navigateToSuperMember(event, superMembers, param.getProject, param.name)
        case v: ScValueOrVariable =>
          val bindings        = v.declaredElements.filter(e => element.textMatches(e.name))
          val signatures      = bindings.flatMap(ScalaPsiUtil.superValsSignatures(_, withSelfType = true))
          val superMembers    = signatures.flatMap(sigToNavigatableElement).toArray
          ScalaNavigationUtils.navigateToSuperMember(event, superMembers, v.getProject, element.getText)
        case ta: ScTypeAlias =>
          val superElements = ScalaPsiUtil.superTypeMembers(ta, withSelfType = true)
          val navigatables = superElements.filterByType[NavigatablePsiElement].toArray
          ScalaNavigationUtils.navigateToSuperType(event, navigatables, ta.getProject, ta.name)
      }.orElse {
        ScalaExportedMemberUtil.exportedMemberOverrideAt(element).map { exportedMember =>
          val superMembers = exportedMember.superSignatures.flatMap(sigToNavigatableElement).toArray
          ScalaNavigationUtils.navigateToSuperMember(event, superMembers, element.getProject, element.getText)
        }
      }
  )

  private def tooltipForExport(element: PsiElement): Option[String] = {
    ScalaExportedMemberUtil.exportedMemberOverrideAt(element).map { exportedMember =>
      overridingTooltip(exportedMember.semantic, exportedMember.superSignatures.map(_.namedElement))
    }
  }

  val overriddenMember: ScalaMarkerType = ScalaMarkerType(
    tooltipProvider = element =>
      namedParent(element).collect {
        case m: ScMember =>
          if (GutterUtil.isAbstract(m)) ScalaBundle.message("has.implementations")
          else ScalaBundle.message("is.overridden.by")
      }.orNull,
    navigationHandler = new ScalaInheritorsLineMarkerNavigator
  )

  def newCellRenderer: PsiElementListCellRenderer[PsiElement] = new ScCellRenderer

  val subclassedClass: ScalaMarkerType = ScalaMarkerType(
    tooltipProvider = element =>
      element.parent.collect {
        case aClass: PsiClass =>
          val inheritors = ClassInheritorsSearch.search(aClass, aClass.getUseScope, true).toArray(PsiClass.EMPTY_ARRAY).toSeq
          val isTooMany = inheritors.size > maxNumberOfElements
          val prefix = aClass match {
            case _: ScTrait =>
              if (isTooMany) ScalaBundle.message("trait.has.several.implementations", inheritors.size)
              else ScalaBundle.message("trait.has.implementations")
            case _ =>
              if (isTooMany) ScalaBundle.message("class.has.several.subclasses", inheritors.size)
              else ScalaBundle.message("class.has.subclasses")
          }
          val shownInheritors = if (isTooMany) emptyList else inheritors.asJava

          GutterTooltipHelper.getTooltipText(shownInheritors,
            prefix,
            false, //do not skip inheritor itself
            IdeActions.ACTION_GOTO_IMPLEMENTATION)

      }.orNull,
    navigationHandler = new ScalaInheritorsLineMarkerNavigator
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
    ScalaMarkerType(tooltipProvider, (event, _) => SAMUtil.singleAbstractMethod(aClass).foreach(navigateToSuperMethod(event, _, aClass.getProject, includeSelf = true)))
  }

  private val maxNumberOfElements = 5

  private def overridesImplementsPrefix(member: PsiElement, supers: Seq[PsiNamedElement]): String = {
    val isTooMany = supers.size > maxNumberOfElements
    val isOverrides = GutterUtil.isOverrides(member, supers)
    member match {
      case _: ScTypeAlias =>
        if (isTooMany) ScalaBundle.message("overrides.type.from.super.several.classes", supers.size)
        else           ScalaBundle.message("overrides.type.prefix")
      case _ =>
        (isOverrides, isTooMany) match {
          case (true, true)   => ScalaBundle.message("overrides.member.from.several.classes", supers.size)
          case (true, false)  => ScalaBundle.message("overrides.member.from.prefix")
          case (false, true)  => ScalaBundle.message("implements.member.from.several.classes", supers.size)
          case (false, false) => ScalaBundle.message("implements.member.from.prefix")
        }
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
        case x: PsiNamedElement if x.nameContext.isInstanceOf[ScMember] =>
          val containing = x.nameContext.asInstanceOf[ScMember].containingClass
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
        case x: PsiNamedElement if x.nameContext != null =>
          x.nameContext.getIcon(getIconFlags)
        case _ => super.getIcon(element)
      }
  }
}

case class ScalaMarkerType(
  tooltipProvider:   com.intellij.util.Function[PsiElement, String],
  navigationHandler: GutterIconNavigationHandler[PsiElement]
)
