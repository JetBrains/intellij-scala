package org.jetbrains.plugins.scala.codeInspection.unusedInspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.psi._
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search._
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.annotator.usageTracker.ScalaRefCountHolder
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.codeInspection.ui.InspectionOptionsComboboxPanel
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{inNameContext, isOnlyVisibleInLocalFile, superValsSignatures}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScFunction, ScFunctionDeclaration, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.impl.search.ScalaOverridingMemberSearcher
import org.jetbrains.plugins.scala.project.{ModuleExt, ScalaLanguageLevel}
import org.jetbrains.plugins.scala.util.SAMUtil.PsiClassToSAMExt
import org.jetbrains.plugins.scala.util.{ScalaMainMethodUtil, ScalaUsageNamesUtil}

import javax.swing.JComponent
import scala.beans.{BeanProperty, BooleanBeanProperty}
import scala.jdk.CollectionConverters._

final class ScalaUnusedDeclarationInspection extends HighlightingPassInspection {

  import ScalaUnusedDeclarationInspection._
  import org.jetbrains.plugins.scala.codeInspection.ui.CompilerInspectionOptions._

  override def isEnabledByDefault: Boolean = true

  override def getDisplayName: String = ScalaInspectionBundle.message("display.name.unused.declaration")

  @BooleanBeanProperty
  var reportPublicDeclarations: Boolean = true

  @BeanProperty
  var reportLocalDeclarations: Int = 0

  override def createOptionsPanel: JComponent = {
    val panel = new InspectionOptionsComboboxPanel(this)
    panel.addCheckbox(
      ScalaInspectionBundle.message("name.unused.declaration.report.public.declarations"),
      "reportPublicDeclarations"
    )
    panel.addComboboxForCompilerOption(
      label = ScalaInspectionBundle.message("name.unused.declaration.report.local.declarations"),
      getSelectedIndex = () => reportLocalDeclarations,
      setSelectedIndex = reportLocalDeclarations = _
    )
    panel
  }

  private def isElementUsed(element: ScNamedElement, isOnTheFly: Boolean): Boolean = {
    if (!isReportingEnabledForElement(element)) {
      true
    } else if (isOnlyVisibleInLocalFile(element)) {
      if (isOnTheFly) {
        localSearch(element)
      } else {
        referencesSearch(element)
      }
    } else if (referencesSearch(element)) {
      true
    } else if (!reportPublicDeclarations) {
      true
    } else if (checkIfEnumUsedOutsideScala(element)) {
      true
    } else if (ScalaPsiUtil.isImplicit(element)) {
      true
    } else {
      textSearch(element)
    }
  }

  private def isReportingEnabledForElement(element: ScNamedElement): Boolean =
    if (reportLocalDeclarations == AlwaysEnabled) {
      true
    } else {
      element.nameContext match {
        case m: ScMember if m.isLocal && reportLocalDeclarations == AlwaysDisabled =>
          false
        case m: ScMember if m.isLocal && reportLocalDeclarations == ComplyToCompilerOption =>
          val compilerOptions = element.module.map(_.scalaCompilerSettings.additionalCompilerOptions).getOrElse(Nil)
          compilerOptions.contains("-Wunused:locals") || compilerOptions.contains("-Wunused:linted") || compilerOptions.contains("-Xlint:unused")
        case _ =>
          true
      }
    }

  // this case is for elements accessible only in a local scope
  private def localSearch(element: ScNamedElement): Boolean = {
    //we can trust RefCounter because references are counted during highlighting
    val refCounter = ScalaRefCountHolder(element)

    var used = false
    val success = refCounter.runIfUnusedReferencesInfoIsAlreadyRetrievedOrSkip { () =>
      used = refCounter.isValueReadUsed(element) || refCounter.isValueWriteUsed(element)
    }

    !success || used // Return true also if runIfUnused... was a failure
  }

  // this case is for elements accessible not only in a local scope, but within the same file
  private def referencesSearch(element: ScNamedElement): Boolean = {
    val elementsForSearch = element match {
      // if the element is an enum case, we also look for usage in a few synthetic methods generated for the enum class
      case enumCase: ScEnumCase =>
        val syntheticMembers =
          ScalaPsiUtil.getCompanionModule(enumCase.enumParent)
            .toSeq.flatMap(_.membersWithSynthetic)
            .collect {
              case n: ScNamedElement if ScalaUsageNamesUtil.enumSyntheticMethodNames.contains(n.name) => n
            }
        enumCase.getSyntheticCounterpart +: syntheticMembers
      case e: ScNamedElement => Seq(e)
    }

    val scope = new LocalSearchScope(element.getContainingFile)
    elementsForSearch.exists(ReferencesSearch.search(_, scope).findFirst() != null)
  }

  // if the element is an enum case, and the enum class is accessed from outside Scala, we assume the enum case is used
  private def checkIfEnumUsedOutsideScala(element: ScNamedElement): Boolean = {
    val scEnum = element match {
      case el: ScEnumCase => Some(el.enumParent)
      case el: ScEnum => Some(el)
      case _ => None
    }

    scEnum.exists { e =>
      var used = false

      val processor = new TextOccurenceProcessor {
        override def execute(e2: PsiElement, offsetInElement: Int): Boolean =
          inReadAction {
            if (e2.getContainingFile.isScala3File || e2.getContainingFile.isScala2File) {
              true
            } else {
              used = true
              false
            }
          }
      }

      PsiSearchHelper
        .getInstance(element.getProject)
        .processElementsWithWord(
          processor,
          element.getUseScope,
          e.getName, // for usage of enum methods through `EnumName.methodName(...)`
          (UsageSearchContext.IN_CODE | UsageSearchContext.IN_FOREIGN_LANGUAGES).toShort,
          true
        )

      if (!used) {
        PsiSearchHelper
          .getInstance(element.getProject)
          .processElementsWithWord(
            processor,
            element.getUseScope,
            s"${e.getName}$$.MODULE$$", // for usage of enum methods through `EnumName$.MODULE$.methodName(...)`
            (UsageSearchContext.IN_CODE | UsageSearchContext.IN_FOREIGN_LANGUAGES).toShort,
            true
          )
      }
      used
    }
  }

  // if the element is accessible from other files, we check that with a text search
  private def textSearch(element: ScNamedElement): Boolean = {
    val helper = PsiSearchHelper.getInstance(element.getProject)
    var used = false
    val processor = new TextOccurenceProcessor {
      override def execute(e2: PsiElement, offsetInElement: Int): Boolean = {
        inReadAction {
          if (element.getContainingFile == e2.getContainingFile) {
            true
          } else {
            used = (e2, Option(e2.getParent)) match {
              case (_, Some(_: ScReferencePattern)) => false
              case (_, Some(_: ScTypeDefinition)) => false
              case (_: PsiIdentifier, _) => true
              case (l: LeafPsiElement, _) if l.isIdentifier => true
              case (_: ScStableCodeReference, _) => true
              case _ => false
            }
            !used
          }
        }
      }
    }

    ScalaUsageNamesUtil.getStringsToSearch(element).asScala.foreach { name =>
      if (!used) {
        helper.processElementsWithWord(
          processor,
          element.getUseScope,
          name,
          (UsageSearchContext.IN_CODE | UsageSearchContext.IN_FOREIGN_LANGUAGES).toShort,
          true
        )
      }
    }
    used
  }

  override def invoke(element: PsiElement, isOnTheFly: Boolean): Seq[ProblemInfo] = {

    if (!shouldProcessElement(element)) {
      Seq.empty
    } else {

      // Structure to encapsulate the possibility to check a delegate element to determine
      // usedness of the original PsiElement passed into `invoke`.
      // When a ProblemInfo is created, we still want it to pertain to the original element under inspection,
      // that was passed into `invoke`, and not to the delegate.
      // So we use the delegate only to determine usedness, and the original for all other operations.
      case class InspectedElement(original: ScNamedElement, delegate: ScNamedElement)

      val elements: Seq[InspectedElement] = element match {
        case functionDeclaration: ScFunctionDeclaration
          if Option(functionDeclaration.getContainingClass).exists(_.isSAMable) =>
          Option(functionDeclaration.getContainingClass).toSeq
            .collect { case named: ScNamedElement => named }
            .map(InspectedElement(functionDeclaration, _))
        case named: ScNamedElement => Seq(InspectedElement(named, named))
        case _ => Seq.empty
      }
      elements.flatMap {
        case InspectedElement(_, _: ScTypeParam) if !isOnTheFly => Seq.empty
        case InspectedElement(_, typeParam: ScTypeParam) if typeParam.hasBounds || typeParam.hasImplicitBounds => Seq.empty
        case InspectedElement(_, inNameContext(holder: PsiAnnotationOwner)) if hasUnusedAnnotation(holder) =>
          Seq.empty
        case InspectedElement(original: ScNamedElement, delegate: ScNamedElement) if !isElementUsed(delegate, isOnTheFly) =>

          val dontReportPublicDeclarationsQuickFix =
            if (isOnlyVisibleInLocalFile(original)) None else Some(new DontReportPublicDeclarationsQuickFix(original))

          val addScalaAnnotationUnusedQuickFix = if (delegate.scalaLanguageLevelOrDefault < ScalaLanguageLevel.Scala_2_13)
            None else Some(new AddScalaAnnotationUnusedQuickFix(original))

          val message = if (isOnTheFly) {
            ScalaUnusedDeclarationInspection.annotationDescription
          } else {
            UnusedDeclarationVerboseProblemInfoMessage(original)
          }

          Seq(
            ProblemInfo(
              original.nameId,
              message,
              ProblemHighlightType.LIKE_UNUSED_SYMBOL,
              DeleteUnusedElementFix.quickfixesFor(original) ++
                dontReportPublicDeclarationsQuickFix ++
                addScalaAnnotationUnusedQuickFix
            )
          )
        case _ =>
          Seq.empty
      }
    }
  }

  override def shouldProcessElement(elem: PsiElement): Boolean = elem match {
    case e if !isOnlyVisibleInLocalFile(e) && TestSourcesFilter.isTestSources(e.getContainingFile.getVirtualFile, e.getProject) => false
    case _: ScSelfTypeElement => false
    case e: ScalaPsiElement if e.module.exists(_.isBuildModule) => false
    case e: PsiElement if UnusedDeclarationInspectionBase.isDeclaredAsEntryPoint(e) && !ScalaPsiUtil.isImplicit(e) => false
    case obj: ScObject if ScalaMainMethodUtil.hasScala2MainMethod(obj) => false
    case n: ScNamedElement if n.nameId == null || n.name == "_" || isOverridingOrOverridden(n) => false
    case n: ScNamedElement =>
      n match {
        case p: ScModifierListOwner if hasOverrideModifier(p) => false
        case fd: ScFunctionDefinition if ScalaMainMethodUtil.isMainMethod(fd) => false
        case f: ScFunction if f.isSpecial || isOverridingFunction(f) || f.isConstructor => false
        case p: ScClassParameter if p.isCaseClassVal || p.isEnumVal || p.isEnumCaseVal => false
        case p: ScParameter =>
          p.parent.flatMap(_.parent.flatMap(_.parent)) match {
            case Some(_: ScFunctionDeclaration) => false
            case Some(f: ScFunctionDefinition) if ScalaOverridingMemberSearcher.search(f).nonEmpty ||
              isOverridingFunction(f) || ScalaMainMethodUtil.isMainMethod(f) => false
            case _ => true
          }
        case _ => true
      }
    case _ => false
  }
}

object ScalaUnusedDeclarationInspection {
  @Nls
  val annotationDescription: String = ScalaInspectionBundle.message("declaration.is.never.used")

  private def hasOverrideModifier(member: ScModifierListOwner): Boolean =
    member.hasModifierPropertyScala(ScalaModifier.OVERRIDE)

  private def isOverridingOrOverridden(element: PsiNamedElement): Boolean =
    superValsSignatures(element, withSelfType = true).nonEmpty || isOverridden(element)

  private def isOverridingFunction(func: ScFunction): Boolean =
    hasOverrideModifier(func) || func.superSignatures.nonEmpty || isOverridden(func)

  private def isOverridden(member: PsiNamedElement): Boolean =
    ScalaOverridingMemberSearcher.search(member, deep = false, withSelfType = true).nonEmpty

  private def hasUnusedAnnotation(holder: PsiAnnotationOwner): Boolean =
    holder.hasAnnotation("scala.annotation.unused") ||
      // not entirely correct, but if we find @nowarn here in this situation
      // we can assume that it is directed at the unusedness of the symbol
      holder.hasAnnotation("scala.annotation.nowarn")
}
