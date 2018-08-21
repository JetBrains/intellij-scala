package org.jetbrains.sbt
package annotator

import com.intellij.lang.annotation.{AnnotationHolder, Annotator}
import com.intellij.psi.{PsiComment, PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeFromText
import org.jetbrains.plugins.scala.lang.psi.types.api.{Nothing, Null}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}
import org.jetbrains.plugins.scala.project.{ProjectContext, Version}
import org.jetbrains.sbt.language.SbtFileImpl
import org.jetbrains.sbt.settings.SbtSettings

/**
 * @author Pavel Fatin
 */
class SbtAnnotator extends Annotator {

  import SbtAnnotator._

  def annotate(element: PsiElement, holder: AnnotationHolder): Unit = element match {
    case file: SbtFileImpl =>
      val project = file.getProject
      val sbtVersion =
        SbtSettings.getInstance(project)
          .getLinkedProjectSettings(file)
          .safeMap(_.sbtVersion)
          .map(Version.apply)
          .getOrElse(Sbt.LatestVersion)
      new Worker(file.children.toVector, sbtVersion, holder)(project).annotate()
    case _ =>
  }

  private class Worker(sbtFileElements: Seq[PsiElement], sbtVersion: Version, holder: AnnotationHolder)
                      (implicit project: ProjectContext) {

    private val allowedTypes: List[String] =
      if (sbtVersion < Version("0.13.0")) AllowedTypes_0_12
      else if (sbtVersion < Version("0.13.6")) AllowedTypes_0_13
      else if (sbtVersion < Version("1.0.0")) AllowedTypes_0_13_6
      else AllowedTypes_1_0

    private val expectedExpressionType: String =
      if (sbtVersion < Version("0.13.6")) SbtBundle("sbt.annotation.expectedExpressionType")
      else SbtBundle("sbt.annotation.expectedExpressionTypeSbt0136")

    private def expressionMustConform(expressionType: ScType) =
      if (sbtVersion < Version("0.13.6")) SbtBundle("sbt.annotation.expressionMustConform", expressionType)
      else SbtBundle("sbt.annotation.expressionMustConformSbt0136", expressionType)

    def annotate(): Unit = {
      sbtFileElements.collect {
        case exp: ScExpression => annotateTypeMismatch(exp)
        case element => annotateNonExpression(element)
      }
      if (sbtVersion < Version("0.13.7"))
        annotateMissingBlankLines()
    }

    private def annotateNonExpression(element: PsiElement): Unit = element match {
      case _: SbtFileImpl | _: ScImportStmt | _: PsiComment | _: PsiWhiteSpace => // no error
      case _: ScFunctionDefinition | _: ScPatternDefinition if sbtVersion > Version("0.13.0") => // no error
      case other => holder.createErrorAnnotation(other, SbtBundle("sbt.annotation.sbtFileMustContainOnlyExpressions"))
    }

    private def annotateTypeMismatch(expression: ScExpression): Unit =
      for {
        expressionType <- expression.`type`().toOption
        message <-
          if (expressionType.equiv(Nothing) || expressionType.equiv(Null))
            Option(expectedExpressionType)
          else if (!isTypeAllowed(expression, expressionType, allowedTypes))
            Option(expressionMustConform(expressionType))
          else None
      } {
        holder.createErrorAnnotation(expression, message)
      }

    private def annotateMissingBlankLines(): Unit =
      sbtFileElements.sliding(3).foreach {
        case Seq(_: ScExpression, space: PsiWhiteSpace, e: ScExpression) if space.getText.count(_ == '\n') == 1 =>
          holder.createErrorAnnotation(e, SbtBundle("sbt.annotation.blankLineRequired", sbtVersion))
        case _ =>
      }

    private def sbtVersionLessThan(version: String): Boolean =
      sbtVersion < Version(version)

  }
}

object SbtAnnotator {
  val AllowedTypes_0_12 = List("Seq[Project.Setting[_]]", "Project.Setting[_]")
  val AllowedTypes_0_13 = List("Seq[Def.SettingsDefinition]", "Def.SettingsDefinition")
  val AllowedTypes_0_13_6 = List("sbt.internals.DslEntry")
  val AllowedTypes_1_0 = List("sbt.internal.DslEntry")

  def isTypeAllowed(expression: ScExpression, expressionType: ScType, allowedTypes: Seq[String]): Boolean = {

    (for {
      typeName <- allowedTypes
      expectedType <- createTypeFromText(typeName, expression.getContext, expression)
      if !expectedType.isAny // this shouldn't happen if context is setup correctly
    } yield {
      lazy val typeAfterImplicits = expression.getTypeAfterImplicitConversion(expectedOption = Option(expectedType)).tr.getOrNothing
      expressionType.conforms(expectedType) || typeAfterImplicits.conforms(expectedType)
    }).exists(identity)

  }
}
