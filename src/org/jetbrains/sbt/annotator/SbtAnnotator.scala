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

    private val allowedTypes =
      if (sbtVersionLessThan("0.13.6")) SbtAnnotator.AllowedTypes013
      else SbtAnnotator.AllowedTypes0136

    private val expectedExpressionType =
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
      expression.getType(TypingContext.empty).foreach { expressionType =>
        if (expressionType.equiv(Nothing) || expressionType.equiv(Null)) {
          holder.createErrorAnnotation(expression, expectedExpressionType)
        } else {
          if (!isTypeAllowed(expression, expressionType))
            holder.createErrorAnnotation(expression, expressionMustConform(expressionType))
        }
      }

    private def isTypeAllowed(expression: ScExpression, expressionType: ScType): Boolean =
      allowedTypes.flatMap { typeName =>
        createTypeFromText(typeName, expression.getContext, expression)
      }.exists { scType =>
        expressionType.conforms(scType)
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
  // SettingsDefinition *should* conform to DslEntry via implicit conversion, but that isn't happening in conformance check. why?
  val AllowedTypes0136 = List("sbt.internals.DslEntry", "Seq[Def.SettingsDefinition]", "Def.SettingsDefinition")
  val AllowedTypes013 = List("Seq[Def.SettingsDefinition]", "Def.SettingsDefinition")

  // conformance always delivers false for these types for some reason. since sbt 0.12 is a low-value target, ignore this problem
  // val AllowedTypes012 = List("Seq[Project.Setting]", "Project.Setting")
}
