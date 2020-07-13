package org.jetbrains.plugins.scala
package annotator
package gutter

import java.awt.event.MouseEvent
import java.util.Collections.singletonList
import java.{util => ju}

import com.intellij.codeInsight.daemon._
import com.intellij.codeInsight.daemon.impl.GutterTooltipHelper
import com.intellij.icons.AllIcons.Gutter
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.{CodeInsightColors, EditorColorsManager}
import com.intellij.openapi.editor.markup.{GutterIconRenderer, SeparatorPlacement}
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.{Function => IJFunction}
import javax.swing.Icon
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClauses, ScPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.{Constructor, ScFieldId, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.types.TermSignature
import org.jetbrains.plugins.scala.util.SAMUtil._

/**
 * User: Alexander Podkhalyuzin
 * Date: 31.10.2008
 */
final class ScalaLineMarkerProvider extends LineMarkerProvider with ScalaSeparatorProvider {

  import Gutter._
  import GutterIconRenderer.Alignment
  import GutterUtil._
  import ScalaMarkerType._

  override def getLineMarkerInfo(element: PsiElement): LineMarkerInfo[_ <: PsiElement] =
    if (element.isValid) {
      val lineMarkerInfo =
        getOverridesImplementsMarkers(element)
          .orElse(getImplementsSAMTypeMarker(element))
          .orElse(companionMarker(element))
          .orNull

      if (DaemonCodeAnalyzerSettings.getInstance().SHOW_METHOD_SEPARATORS && isSeparatorNeeded(element)) {
        if (lineMarkerInfo == null) {
          addSeparatorInfo(createMarkerInfo(element))
        } else {
          addSeparatorInfo(lineMarkerInfo)
        }
      } else lineMarkerInfo
    } else null

  private[this] def createMarkerInfo(element: PsiElement): LineMarkerInfo[PsiElement] = {
    val leaf = PsiTreeUtil.firstChild(element).toOption.getOrElse(element)
    new LineMarkerInfo[PsiElement](
      leaf,
      leaf.getTextRange,
      null: Icon,
      Function.const(null)(_),
      null: GutterIconNavigationHandler[PsiElement],
      Alignment.RIGHT
    )
  }

  private[this] def addSeparatorInfo(info: LineMarkerInfo[_ <: PsiElement]): LineMarkerInfo[_ <: PsiElement] = {
    info.separatorColor =
      EditorColorsManager.getInstance().getGlobalScheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR)
    info.separatorPlacement = SeparatorPlacement.TOP
    info
  }

  private[this] def arrowUpLineMarker(element: PsiElement,
                                      icon: Icon,
                                      markerType: ScalaMarkerType,
                                      presentationParent: Option[PsiElement] = None): LineMarkerInfo[PsiElement] = {

    val info = ArrowUpOrDownLineMarkerInfo(element, icon, markerType, Alignment.LEFT, presentationParent)
    NavigateAction.setNavigateAction(info, ScalaBundle.message("go.to.super.method"), IdeActions.ACTION_GOTO_SUPER)
  }

  /* Validates that this psi element can be the first one in a lambda */
  private[this] def canBeFunctionalExpressionAnchor(e: PsiElement): Boolean =
    e.getNode.getElementType match {
      case ScalaTokenTypes.tLBRACE => e.getNextSiblingNotWhitespace.isInstanceOf[ScCaseClauses]
      case ScalaTokenTypes.tIDENTIFIER | ScalaTokenTypes.tUNDER | ScalaTokenTypes.tLPARENTHESIS =>
        true
      case _ => false
    }

  private[this] def funExprParent(element: PsiElement): Option[(ScExpression, PsiClass)] =
    element.parentsInFile.collectFirst {
      case _: ScMember                    => None
      case e @ SAMTypeImplementation(sam) => Option(e -> sam)
    }.flatten

  private[this] val trivialSAMs: Set[String] = Set("scala.Function", "scala.PartialFunction", "java.util.function")
  private[this] def isInterestingSAM(sam: PsiClass): Boolean = !trivialSAMs.exists(sam.qualifiedName.startsWith)

  private[this] def getImplementsSAMTypeMarker(element: PsiElement): Option[LineMarkerInfo[_ <: PsiElement]] =
    if (canBeFunctionalExpressionAnchor(element)) for {
      (parent, sam) <- funExprParent(element)
      if isInterestingSAM(sam) &&
        PsiTreeUtil.getDeepestFirst(parent) == element
      icon = ImplementingFunctionalInterface
      markerType = samTypeImplementation(sam)
    } yield arrowUpLineMarker(element, icon, markerType, Option(parent))
    else None

  private[this] def getOverridesImplementsMarkers(element: PsiElement): Option[LineMarkerInfo[_ <: PsiElement]] = {
    val isIdentifier = element.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER
    val notReference = element.parent.exists {
      case _: ScReference => false
      case _              => true
    }

    if (isIdentifier && notReference) {
      def containsNamedElement(holder: ScDeclaredElementsHolder) =
        holder.declaredElements.exists(_.asInstanceOf[ScNamedElement].nameId == element)

      val text = element.getText

      namedParent(element).flatMap {
        case method: ScFunction if !method.isLocal && method.name == text =>
          val signatures = method.superSignaturesIncludingSelfType
          val icon       = getOverridesOrImplementsIcon(method, signatures)
          val markerType = overridingMember
          if (signatures.nonEmpty) arrowUpLineMarker(element, icon, markerType).toOption
          else None
        case cParam: ScClassParameter if cParam.name == text =>
          val signatures = ScalaPsiUtil.superValsSignatures(cParam, withSelfType = true)
          val icon       = getOverridesOrImplementsIcon(cParam, signatures)
          val markerType = overridingMember
          if (signatures.nonEmpty) arrowUpLineMarker(element, icon, markerType).toOption
          else None
        case v: ScValueOrVariable if !v.isLocal && containsNamedElement(v) =>
          val bindings   = v.declaredElements.filter(e => element.textMatches(e.name))
          val signatures = bindings.flatMap(ScalaPsiUtil.superValsSignatures(_, withSelfType = true))
          val icon       = getOverridesOrImplementsIcon(v, signatures)
          val markerType = overridingMember
          if (signatures.nonEmpty) arrowUpLineMarker(element, icon, markerType).toOption
          else None
        case ta: ScTypeAlias if !ta.isLocal && ta.name == text =>
          val elements = ScalaPsiUtil.superTypeMembers(ta, withSelfType = true)
          val icon = ImplementingMethod
          val typez = overridingMember
          if (elements.nonEmpty) arrowUpLineMarker(element, icon, typez).toOption
          else None
        case _ => None
      }
    } else None
  }


  override def collectSlowLineMarkers(elements: ju.List[_ <: PsiElement],
                                      result: ju.Collection[_ >: LineMarkerInfo[_]]): Unit = {
    import scala.collection.JavaConverters._

    ApplicationManager.getApplication.assertReadAccessAllowed()
    elements.asScala.collect {
      case ident if ident.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER => ident
    }.flatMap { identifier =>
      ProgressManager.checkCanceled()
      val context = identifier.parent match {
        case Some(_: ScPattern | _: ScFieldId) => namedParent(identifier)
        case other                             => other
      }

      context match {
        case Some(tDef: ScTypeDefinition)                => collectInheritingClassesMarker(tDef)
        case Some(member: ScMember) if !member.isLocal => collectOverriddenMemberMarker(member, identifier)
        case _                                           => None
      }
    }.foreach(result.add)
  }
}

// TODO Split methods between a companion object and packet object (so that methods can be more private)
private object GutterUtil {

  import Gutter._
  import GutterIconRenderer.Alignment
  import ScalaMarkerType._

  private[gutter] final case class ArrowUpOrDownLineMarkerInfo(
                                                                element:            PsiElement,
                                                                icon:               Icon,
                                                                markerType:         ScalaMarkerType,
                                                                alignment: Alignment,
                                                                presentationParent: Option[PsiElement] = None
  ) extends MergeableLineMarkerInfo[PsiElement](
        element,
        element.getTextRange,
        icon,
        markerType.tooltipProvider,
        markerType.navigationHandler,
        alignment
      ) {
    override def canMergeWith(other: MergeableLineMarkerInfo[_]): Boolean = other match {
      case that: ArrowUpOrDownLineMarkerInfo => icon == that.icon
      case _                                 => false
    }

    override def getCommonIcon(infos: ju.List[_ <: MergeableLineMarkerInfo[_]]): Icon = icon


    override def getCommonTooltip(infos: ju.List[_ <: MergeableLineMarkerInfo[_]]): IJFunction[_ >: PsiElement, String] =
      _ =>
        markerType match {
          case ScalaMarkerType.overriddenMember => ScalaBundle.message("multiple.overriden.tooltip")
          case _                                => ScalaBundle.message("multiple.overriding.tooltip")
      }

    override def getElementPresentation(element: PsiElement): String =
      presentationParent.fold(super.getElementPresentation(element))(
        parent => StringUtil.shortenTextWithEllipsis(parent.getText, 100, 0)
      )
  }

  def getOverridesOrImplementsIcon(element: PsiElement, signatures: Seq[TermSignature]): Icon =
    if (isOverrides(element, signatures)) OverridingMethod else ImplementingMethod

  def namedParent(e: PsiElement): Option[PsiElement] =
    e.withParentsInFile.find(ScalaPsiUtil.isNameContext)

  def collectInheritingClassesMarker(aClass: ScTypeDefinition): Option[LineMarkerInfo[_ <: PsiElement]] = {
    val inheritor = ClassInheritorsSearch.search(aClass, false).findFirst.toOption
    inheritor.map { _ =>
      val range = aClass.nameId.getTextRange

      val icon = aClass match {
        case _: ScTrait => ImplementedMethod
        case _ => OverridenMethod
      }

      val info = new LineMarkerInfo(
        aClass.nameId,
        range,
        icon,
        subclassedClass.tooltipProvider,
        subclassedClass.navigationHandler,
        Alignment.RIGHT
      )
      NavigateAction.setNavigateAction(info, ScalaBundle.message("go.to.implementation"), IdeActions.ACTION_GOTO_IMPLEMENTATION)
    }
  }

  def collectOverriddenMemberMarker(member: ScMember, anchor: PsiElement): Option[LineMarkerInfo[_ <: PsiElement]] =
    member match {
      case Constructor(_) => None
      case _ =>

        ScalaMarkerType.findOverrides(member, deep = false).nonEmpty.option {
          val info = ArrowUpOrDownLineMarkerInfo(
            anchor,
            if (isAbstract(member)) ImplementedMethod else OverridenMethod,
            overriddenMember,
            Alignment.RIGHT
          )
          NavigateAction.setNavigateAction(info, ScalaBundle.message("go.to.super.method"), IdeActions.ACTION_GOTO_SUPER)
        }
    }

  def isOverrides(element: PsiElement, supers: Seq[TermSignature]): Boolean =
    element match {
      case _: ScFunctionDeclaration  => true
      case _: ScValueDeclaration     => true
      case _: ScVariableDeclaration  => true
      case _: ScTypeAliasDeclaration => true
      case _ =>
        val iter = supers.iterator
        while (iter.hasNext) {
          val s = iter.next()
          ScalaPsiUtil.nameContext(s.namedElement) match {
            case _: ScFunctionDefinition                          => return true
            case _: ScFunction                                    =>
            case method: PsiMethod if !method.hasAbstractModifier => return true
            case _: ScVariableDefinition | _: ScPatternDefinition => return true
            case f: PsiField if !f.hasAbstractModifier            => return true
            case _: ScVariableDeclaration                         =>
            case _: ScValueDeclaration                            =>
            case _: ScParameter                                   => return true
            case _: ScTypeAliasDefinition                         => return true
            case _: ScTypeAliasDeclaration                        =>
            case _: PsiClass                                      => return true
            case _                                                =>
          }
        }
        false
    }

  def isAbstract(element: PsiElement): Boolean = element match {
    case _: ScFunctionDeclaration  => true
    case _: ScValueDeclaration     => true
    case _: ScVariableDeclaration  => true
    case _: ScTypeAliasDeclaration => true
    case _                         => false
  }

  // Show companion for class / trait / object in the gutter, https://youtrack.jetbrains.com/issue/SCL-17697
  private[gutter] def companionMarker(element: PsiElement): Option[LineMarkerInfo[_ <: PsiElement]] =
    // TODO Enable in tests when GutterMarkersTest will be able to separate different maker providers
    if (ApplicationManager.getApplication.isUnitTestMode) None else element match {
      case identifier @ ElementType(ScalaTokenTypes.tIDENTIFIER) && Parent(_: ScClass | _: ScTrait | _: ScObject) =>
        val typeDefinition = identifier.getParent.asInstanceOf[ScTypeDefinition]

        typeDefinition.baseCompanionModule.map { companion =>
          val swapped = typeDefinition.startOffset > companion.startOffset ^ typeDefinition.is[ScObject]

          val info = new LineMarkerInfo(identifier,
            identifier.getTextRange,
            iconFor(typeDefinition, swapped),
            (_: PsiElement) =>
              GutterTooltipHelper.getTooltipText(singletonList(companion.nameId.getPrevSiblingNotWhitespace), ScalaBundle.message("has.companion", nameOf(companion)), false, IdeActions.ACTION_GOTO_DECLARATION)
                .replace("to navigate", "on the keyword to navigate"), // Not internationalizable (which is somewhat OK).
            (_: MouseEvent, _: PsiElement) =>
              Option(PsiNavigationSupport.getInstance.createNavigatable(companion.getProject,
                companion.getContainingFile.getVirtualFile, companion.nameId.getPrevSiblingNotWhitespace.startOffset)).foreach(_.navigate(true)),
            Alignment.LEFT)
          NavigateAction.setNavigateAction(info, ScalaBundle.message("go.to.companion", nameOf(companion)), IdeActions.ACTION_GOTO_DECLARATION)
        }

      case _ => None
    }

  private[this] def nameOf(definition: ScTypeDefinition) = definition match {
    case _: ScClass => ScalaBundle.message("companion.class")
    case _: ScTrait => ScalaBundle.message("companion.trait")
    case _: ScObject => ScalaBundle.message("companion.object")
    case _ => "" // Just "Has a companion" is OK.
  }

  private[this] def iconFor(definition: ScTypeDefinition, swapped: Boolean): Icon = definition match {
    case _: ScClass => if (swapped) Icons.CLASS_COMPANION_SWAPPED else Icons.CLASS_COMPANION
    case _: ScTrait => if (swapped) Icons.TRAIT_COMPANION_SWAPPED else Icons.TRAIT_COMPANION
    case _: ScObject => if (swapped) Icons.OBECT_COMPANION_SWAPPED else Icons.OBECT_COMPANION
    case _ => null
  }
}
