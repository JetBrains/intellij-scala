package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy

import com.intellij.codeInspection.{LocalQuickFixAndIntentionActionOnPsiElement, ProblemHighlightType, SetInspectionOptionFix}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiAnnotationOwner, PsiElement, PsiFile}
import org.jetbrains.annotations.{Nls, NonNls}
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.Pipeline
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.{SearchMethodsWithProjectBoundCache, ElementUsage, Search}
import org.jetbrains.plugins.scala.codeInspection.ui.InspectionOptionsComboboxPanel
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{inNameContext, isOnlyVisibleInLocalFile}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDeclaration
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.project.{ModuleExt, ScalaLanguageLevel}
import org.jetbrains.plugins.scala.util.SAMUtil.PsiClassToSAMExt

import javax.swing.JComponent
import scala.beans.{BeanProperty, BooleanBeanProperty}

final class ScalaUnusedDeclarationInspection extends HighlightingPassInspection {

  import ScalaUnusedDeclarationInspection._
  import org.jetbrains.plugins.scala.codeInspection.ui.CompilerInspectionOptions._

  override def isEnabledByDefault: Boolean = true

  @BooleanBeanProperty
  var reportPublicDeclarations: Boolean = true

  @BeanProperty
  var reportLocalDeclarations: Int = 0

  override def createOptionsPanel: JComponent = {
    val panel = new InspectionOptionsComboboxPanel(this)
    panel.addCheckbox(
      ScalaInspectionBundle.message("name.unused.declaration.report.public.declarations"),
      reportPublicDeclarationsPropertyName
    )
    panel.addComboboxForCompilerOption(
      label = ScalaInspectionBundle.message("name.unused.declaration.report.local.declarations"),
      getSelectedIndex = () => reportLocalDeclarations,
      setSelectedIndex = reportLocalDeclarations = _
    )
    panel
  }

  override def invoke(element: PsiElement, isOnTheFly: Boolean): Seq[ProblemInfo] = {
    // Structure to encapsulate the possibility to check a delegate element to determine
    // usedness of the original PsiElement passed into `invoke`.
    // When a ProblemInfo is created, we still want it to pertain to the original element under inspection,
    // that was passed into `invoke`, and not to the delegate.
    // So we use the delegate only to determine usedness, and the original for all other operations.
    case class InspectedElement(original: ScNamedElement, delegate: ScNamedElement)

    val elements: Seq[InspectedElement] = element match {
      case functionDeclaration: ScFunctionDeclaration
        if Option(functionDeclaration.containingClass).exists(_.isSAMable) =>
        Option(functionDeclaration.containingClass).toSeq
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
      case InspectedElement(original: ScNamedElement, delegate: ScNamedElement) =>

        val usages = getPipeline(element.getProject, reportPublicDeclarations).runSearchPipeline(delegate, isOnTheFly)

        if (usages.isEmpty) {

          val dontReportPublicDeclarationsQuickFix = if (isOnlyVisibleInLocalFile(original)) None
          else Some(createDontReportPublicDeclarationsQuickFix(original))

          val addScalaAnnotationUnusedQuickFix = if (delegate.scalaLanguageLevelOrDefault < ScalaLanguageLevel.Scala_2_13) None
          else Some(new AddScalaAnnotationUnusedQuickFix(original))

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
        } else Seq.empty
      case _ => Seq.empty
    }
  }

  /*
  * There is already a method `org.jetbrains.plugins.scala.createSetInspectionOptionFix which does almost the same,
  * but it returns a `LocalQuickFix` and currently the `ProblemInfo` class, which we use to keep fixes for `ScalaUnusedDeclarationInspection`,
  * accepts only `LocalQuickFixAndIntentionActionOnPsiElement`.
  * @todo: If we manage to switch to `LocalQuickFix` in `ProblemInfo`, we can get rid of this method.
  */
  private def createDontReportPublicDeclarationsQuickFix(elem: ScNamedElement): LocalQuickFixAndIntentionActionOnPsiElement = {
    val fix = new SetInspectionOptionFix(
      this,
      reportPublicDeclarationsPropertyName,
      ScalaInspectionBundle.message("fix.unused.declaration.report.public.declarations"),
      false
    )
    new LocalQuickFixAndIntentionActionOnPsiElement(elem) {
      override def invoke(project: Project, file: PsiFile, editor: Editor, startElement: PsiElement, endElement: PsiElement): Unit =
        fix.applyFix(project, elem.getContainingFile)

      override def getText: String = fix.getName

      override def getFamilyName: String = fix.getFamilyName
    }
  }

  override def shouldProcessElement(element: PsiElement): Boolean =
    Search.Util.shouldProcessElement(element) && {
      element match {
        case n: ScNamedElement =>
          n.nameContext match {
            case m: ScMember if m.isLocal =>
              if (reportLocalDeclarations == AlwaysDisabled) {
                false
              } else if (reportLocalDeclarations == ComplyToCompilerOption) {
                n.module.toSeq.flatMap(_.scalaCompilerSettings.additionalCompilerOptions).exists { compilerOption =>
                  compilerOption.equals("-Wunused:locals") ||
                    compilerOption.equals("-Wunused:linted") ||
                    compilerOption.equals("-Xlint:unused")
                }
              } else {
                true
              }
            case _ => true
          }
        case _ => true
      }
    }
}

object ScalaUnusedDeclarationInspection {
  @Nls
  val annotationDescription: String = ScalaInspectionBundle.message("declaration.is.never.used")

  @NonNls
  private val reportPublicDeclarationsPropertyName: String = "reportPublicDeclarations"

  private def hasUnusedAnnotation(holder: PsiAnnotationOwner): Boolean =
    holder.hasAnnotation("scala.annotation.unused") ||
      // not entirely correct, but if we find @nowarn here in this situation
      // we can assume that it is directed at the unusedness of the symbol
      holder.hasAnnotation("scala.annotation.nowarn")

  private def getPipeline(project: Project, reportPublicDeclarations: Boolean): Pipeline = {

    val canExit = (_: ElementUsage) => true

    val searcher = SearchMethodsWithProjectBoundCache(project)

    val localSearch = searcher.LocalSearchMethods

    val globalSearch = if (reportPublicDeclarations) {
      searcher.GlobalSearchMethods
    } else Seq.empty

    new Pipeline(localSearch ++ globalSearch, canExit)
  }
}
