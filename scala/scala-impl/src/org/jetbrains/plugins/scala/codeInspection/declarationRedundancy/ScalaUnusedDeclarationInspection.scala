package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy

import com.intellij.codeInspection.SetInspectionOptionFix
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiAnnotationOwner, PsiElement}
import org.jetbrains.annotations.{Nls, NonNls}
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.Pipeline
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.{ElementUsage, Search, SearchMethodsWithProjectBoundCache}
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

    val pipeline = getPipeline(element.getProject, reportPublicDeclarations)

    def isFunctionDeclarationOfUsedSAMableClass: Boolean = element match {
      case functionDeclaration: ScFunctionDeclaration =>
        val containingClass = functionDeclaration.containingClass
        Option(containingClass).exists(c => c.isSAMable && pipeline.runSearchPipeline(c, isOnTheFly).nonEmpty)
      case _ => false
    }

    element match {
      case _: ScTypeParam if !isOnTheFly => Seq.empty
      case typeParam: ScTypeParam if typeParam.hasBounds || typeParam.hasImplicitBounds => Seq.empty
      case inNameContext(holder: PsiAnnotationOwner) if hasUnusedAnnotation(holder) => Seq.empty
      case named: ScNamedElement =>
        val usages = pipeline.runSearchPipeline(named, isOnTheFly)

        if (usages.isEmpty && !isFunctionDeclarationOfUsedSAMableClass) {

          val dontReportPublicDeclarationsQuickFix = if (isOnlyVisibleInLocalFile(named)) None
          else Some(
            new SetInspectionOptionFix(
              this,
              reportPublicDeclarationsPropertyName,
              ScalaInspectionBundle.message("fix.unused.declaration.report.public.declarations"),
              false
            )
          )

          val addScalaAnnotationUnusedQuickFix = if (named.scalaLanguageLevelOrDefault < ScalaLanguageLevel.Scala_2_13) None
          else Some(new AddScalaAnnotationUnusedQuickFix(named))

          val message = if (isOnTheFly) {
            ScalaUnusedDeclarationInspection.annotationDescription
          } else {
            UnusedDeclarationVerboseProblemInfoMessage(named)
          }

          Seq(
            ProblemInfo(
              named.nameId,
              message,
              DeleteUnusedElementFix.quickfixesFor(named) ++
                dontReportPublicDeclarationsQuickFix ++
                addScalaAnnotationUnusedQuickFix
            )
          )
        } else Seq.empty
      case _ => Seq.empty
    }
  }

  override def shouldProcessElement(element: PsiElement): Boolean =
    Search.Util.shouldProcessElement(element) && {
      element match {
        case n: ScNamedElement =>
          if (isOnlyVisibleInLocalFile(n)) {
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
          } else {
            reportPublicDeclarations
          }
        case _ => false
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
