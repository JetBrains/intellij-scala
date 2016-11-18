package org.jetbrains.sbt
package annotator

import com.intellij.lang.annotation.{AnnotationHolder, Annotator}
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiComment, PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeFromText
import org.jetbrains.plugins.scala.lang.psi.types.api.{Nothing, Null, TypeSystem}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.sbt.language.SbtFileImpl
import org.jetbrains.sbt.settings.SbtSystemSettings

/**
 * @author Pavel Fatin
 */
class SbtAnnotator extends Annotator {

  import SbtAnnotator._

  def annotate(element: PsiElement, holder: AnnotationHolder): Unit = element match {
    case file: SbtFileImpl =>
      val project = file.getProject
      implicit val typeSystem = project.typeSystem
      val sbtVersion =
        SbtSystemSettings.getInstance(project)
          .getLinkedProjectSettings(file)
          .safeMap(_.sbtVersion)
          .getOrElse(Sbt.LatestVersion)
      new Worker(file.children.toVector, sbtVersion, holder).annotate
    case _ =>
  }

  private class Worker(sbtFileElements: Seq[PsiElement], sbtVersion: String, holder: AnnotationHolder)
                      (implicit typeSystem: TypeSystem) {

    private val allowedTypes: List[String] =
      if (sbtVersionLessThan("0.13.0")) AllowedTypes012
      else if (sbtVersionLessThan("0.13.6")) AllowedTypes013
      else AllowedTypes0136

    private val expectedExpressionType: String =
      if (sbtVersionLessThan("0.13.6")) SbtBundle("sbt.annotation.expectedExpressionType")
      else SbtBundle("sbt.annotation.expectedExpressionTypeSbt0136")

    private def expressionMustConform(expressionType: ScType) =
      if (sbtVersionLessThan("0.13.6")) SbtBundle("sbt.annotation.expressionMustConform", expressionType)
      else SbtBundle("sbt.annotation.expressionMustConformSbt0136", expressionType)

    def annotate(implicit typeSystem: TypeSystem) {
      sbtFileElements.collect {
        case exp: ScExpression => annotateTypeMismatch(exp)
        case element => annotateNonExpression(element)
      }
      if (sbtVersionLessThan("0.13.7"))
        annotateMissingBlankLines()
    }

    private def annotateNonExpression(element: PsiElement): Unit = element match {
      case _: SbtFileImpl | _: ScImportStmt | _: PsiComment | _: PsiWhiteSpace =>
      case _: ScFunctionDefinition | _: ScPatternDefinition if !sbtVersionLessThan("0.13.0") =>
      case other => holder.createErrorAnnotation(other, SbtBundle("sbt.annotation.sbtFileMustContainOnlyExpressions"))
    }

    private def annotateTypeMismatch(expression: ScExpression) =
      for {
        expressionType <- expression.getType(TypingContext.empty).toOption
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
      StringUtil.compareVersionNumbers(sbtVersion, version) < 0

  }
}

object SbtAnnotator {
  val AllowedTypes012 = List("Seq[Project.Setting[_]]", "Project.Setting[_]")
  val AllowedTypes013 = List("Seq[Def.SettingsDefinition]", "Def.SettingsDefinition")
  val AllowedTypes0136 = List("sbt.internals.DslEntry")

  def isTypeAllowed(expression: ScExpression, expressionType: ScType, allowedTypes: Seq[String])(implicit typeSystem: TypeSystem): Boolean =
    allowedTypes.flatMap { typeName =>
      createTypeFromText(typeName, expression.getContext, expression)
    }.exists { expectedType =>
      lazy val typeAfterImplicits = expression.getTypeAfterImplicitConversion(expectedOption = Option(expectedType)).tr.getOrNothing
      expressionType.conforms(expectedType) || typeAfterImplicits.conforms(expectedType)
    }
}
