package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy

import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.{checkbox, dropdown, option, pane}
import com.intellij.codeInspection.{LocalQuickFix, UpdateInspectionOptionFix}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiAnnotationOwner, PsiElement}
import org.jetbrains.annotations.{Nls, NonNls}
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.Search.Pipeline
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.cheapRefSearch.{ElementUsage, Search, SearchMethodsWithProjectBoundCache}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{inNameContext, isOnlyVisibleInLocalFile}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDeclaration
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.project.{ModuleExt, ScalaLanguageLevel}
import org.jetbrains.plugins.scala.util.SAMUtil.PsiClassToSAMExt

import scala.beans.{BeanProperty, BooleanBeanProperty}

final class ScalaUnusedDeclarationInspection extends HighlightingPassInspection {

  import ScalaUnusedDeclarationInspection._
  import org.jetbrains.plugins.scala.codeInspection.ui.CompilerInspectionOptions._

  override def isEnabledByDefault: Boolean = true

  @BooleanBeanProperty
  var reportPublicDeclarations: Boolean = true

  @BeanProperty
  var reportLocalDeclarations: Int = 0

  @BooleanBeanProperty
  var enableInScala3: Boolean = false //TODO

  override def getOptionsPane: OptPane = pane(
    checkbox(reportPublicDeclarationsPropertyName, ScalaInspectionBundle.message("name.unused.declaration.report.public.declarations")),
    checkbox(enableInScala3PropertyName, ScalaInspectionBundle.message("enable.in.scala.3")), //TODO
    dropdown(
      reportLocalDeclarationsPropertyName,
      ScalaInspectionBundle.message("name.unused.declaration.report.local.declarations"),
      option(AlwaysEnabled.toString, ScalaInspectionBundle.message("inspection.option.enabled")),
      option(ComplyToCompilerOption.toString, ScalaInspectionBundle.message("inspection.option.check.compiler.unnamed")),
      option(AlwaysDisabled.toString, ScalaInspectionBundle.message("inspection.option.disabled")),
    )
  )

  override def invoke(element: PsiElement, isOnTheFly: Boolean): Seq[ProblemInfo] = {

    val pipeline = getPipeline(element.getProject, reportPublicDeclarations)

    def isFunctionDeclarationOfUsedSAMableClass: Boolean = element match {
      case functionDeclaration: ScFunctionDeclaration =>
        val containingClass = functionDeclaration.containingClass
        Option(containingClass).exists(c => c.isSAMable && pipeline.runSearchPipeline(c, isOnTheFly).nonEmpty)
      case _ => false
    }

    element match {
      case typeParam: ScTypeParam if !isOnTheFly || typeParam.hasBounds || typeParam.hasImplicitBounds ||
        typeParam.owner.asOptionOf[ScTypeDefinition].exists(!_.getModifierList.isFinal) => Seq.empty
      case inNameContext(holder: PsiAnnotationOwner) if hasUnusedAnnotation(holder) => Seq.empty
      case named: ScNamedElement =>
        val usages = pipeline.runSearchPipeline(named, isOnTheFly)

        if (usages.isEmpty && !isFunctionDeclarationOfUsedSAMableClass) {

          val dontReportPublicDeclarationsQuickFix = if (isOnlyVisibleInLocalFile(named)) None
          else Some(
            LocalQuickFix.from(
              new UpdateInspectionOptionFix(
                this,
                reportPublicDeclarationsPropertyName,
                ScalaInspectionBundle.message("fix.unused.declaration.report.public.declarations"),
                false
              )
            )
          )

          val addScalaAnnotationUnusedQuickFix =
            if (named.scalaLanguageLevelOrDefault < ScalaLanguageLevel.Scala_2_13 || named.is[ScTypeParam]) {
              None
            } else {
              Some(new AddScalaAnnotationUnusedQuickFix(named))
            }

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

  override def shouldProcessElement(element: PsiElement): Boolean = {

    def isTopLevelDefinitionInWorksheetFile(m: ScMember): Boolean = {
      val isWorksheetFile = m.getContainingFile.asOptionOf[ScalaFile].exists(_.isWorksheetFile)
      val isTopLevelMember = m.isTopLevel
      isWorksheetFile && isTopLevelMember
    }

    (enableInScala3 || !element.isInScala3File) && Search.Util.shouldProcessElement(element) && {
      element match {
        case m: ScMember if isTopLevelDefinitionInWorksheetFile(m) => false
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
}

object ScalaUnusedDeclarationInspection {
  @Nls
  val annotationDescription: String = ScalaInspectionBundle.message("declaration.is.never.used")

  @NonNls
  private val reportPublicDeclarationsPropertyName: String = "reportPublicDeclarations"

  @NonNls
  private val enableInScala3PropertyName: String = "enableInScala3"

  @NonNls
  private val reportLocalDeclarationsPropertyName: String = "reportLocalDeclarations"

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

    val extensionPointImplementationSearch = searcher.IJExtensionPointImplementationSearch

    new Pipeline(localSearch ++ globalSearch :+ extensionPointImplementationSearch, canExit)
  }
}
