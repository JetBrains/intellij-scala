package org.jetbrains.sbt
package annotator

import com.intellij.lang.annotation.{AnnotationHolder, Annotator}
import com.intellij.psi.{PsiComment, PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.annotator.annotationHolder.ScalaAnnotationHolderAdapter
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.project.{ModuleExt, Version}
import org.jetbrains.sbt.language.SbtFileImpl

final class SbtAnnotator extends Annotator {

  import SbtAnnotator._

  override def annotate(element: PsiElement, holder: AnnotationHolder): Unit =
    annotate(element)(new ScalaAnnotationHolderAdapter(holder))

  def annotate(element: PsiElement)(holder: ScalaAnnotationHolder): Unit = element match {
    case file: SbtFileImpl =>
      val sbtVersion = file.module
        .flatMap(_.sbtVersion)
        .getOrElse(Sbt.LatestVersion)

      val less_13_6 = sbtVersion < Version("0.13.6")
      val allowedTypes =
        if (sbtVersion < Version("0.13.0"))
          "Seq[Project.Setting[_]]" :: "Project.Setting[_]" :: Nil
        else if (less_13_6)
          "Seq[Def.SettingsDefinition]" :: "Def.SettingsDefinition" :: Nil
        else if (sbtVersion < Version("1.0.0"))
          "sbt.internals.DslEntry" :: Nil
        else
          "sbt.internal.DslEntry" :: Nil

      val children = file.getChildren
      for {
        child <- children

        message <- child match {
          case expression: ScExpression =>
            for {
              expressionType <- expression.`type`().toOption

              message =
              if (expressionType.isNothing || expressionType.isNull) {
                if (less_13_6) SbtBundle.message("sbt.annotation.expectedExpressionType")
                else SbtBundle.message("sbt.annotation.expectedExpressionTypeSbt0136")
              } else if (isTypeAllowed(expression, expressionType, allowedTypes: _*)) {
                null
              } else {
                if (less_13_6) SbtBundle.message("sbt.annotation.expressionMustConform", expressionType)
                else SbtBundle.message("sbt.annotation.expressionMustConformSbt0136", expressionType)
              }
              if message != null
            } yield message
          case _: SbtFileImpl |
               _: ScImportStmt |
               _: PsiComment |
               _: PsiWhiteSpace => None
          case _: ScFunctionDefinition |
               _: ScPatternDefinition if sbtVersion > Version("0.13.0") => None
          case _ => Some(SbtBundle.message("sbt.annotation.sbtFileMustContainOnlyExpressions"))
        }
      } holder.createErrorAnnotation(child, message)

      if (sbtVersion < Version("0.13.7")) {
        for {
          expression <- missingBlankLines(children.toSeq)
          message = SbtBundle.message("sbt.annotation.blankLineRequired", sbtVersion)
        } holder.createErrorAnnotation(expression, message)
      }
    case _ =>
  }
}

object SbtAnnotator {

  def isTypeAllowed(expression: ScExpression,
                    expressionType: ScType,
                    allowedTypes: String*): Boolean = {
    val maybeExpectedType = for {
      typeName <- allowedTypes
      typeElement = ScalaPsiElementFactory.createTypeElementFromText(typeName, expression.getContext, expression)
      expectedType <- typeElement.`type`().toOption
    } yield expectedType

    maybeExpectedType.exists { expectedType =>
      expressionType.conforms(expectedType) ||
        expression.getTypeAfterImplicitConversion(expectedOption = Option(expectedType)).tr.getOrNothing.conforms(expectedType)
    }
  }

  private def missingBlankLines(elements: Seq[PsiElement]) =
    elements.sliding(3).flatMap {
      case Seq(_: ScExpression, space: PsiWhiteSpace, expression: ScExpression) if space.getText.count(_ == '\n') == 1 => Some(expression)
      case _ => None
    }
}
