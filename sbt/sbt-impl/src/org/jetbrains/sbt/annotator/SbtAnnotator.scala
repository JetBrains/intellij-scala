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
import org.jetbrains.plugins.scala.lang.psi.types.*
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectPsiFileExt}
import org.jetbrains.sbt.language.SbtFileImpl

// TODO: we need to review SBT 2.0 new rules and adopt SbtAnnotator logic
final class SbtAnnotator extends Annotator {

  import SbtAnnotator._

  override def annotate(element: PsiElement, holder: AnnotationHolder): Unit =
    annotate(element)(new ScalaAnnotationHolderAdapter(holder))

  def annotate(element: PsiElement)(holder: ScalaAnnotationHolder): Unit = element match {
    case file: SbtFileImpl =>
      val sbtVersion = file.module
        .flatMap(_.sbtVersion).map(SbtVersion(_))
        .getOrElse(SbtVersion.Latest.Sbt_1)

      val allowedTypes =
        if (sbtVersion.isSbt0)
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
                SbtBundle.message("sbt.annotation.expectedExpressionTypeSbt0136")
              } else if (isTypeAllowed(expression, expressionType, allowedTypes*)) {
                null
              } else {
                SbtBundle.message("sbt.annotation.expressionMustConformSbt0136", expressionType)
              }
              if message != null
            } yield message
          case _: SbtFileImpl |
               _: ScImportStmt |
               _: PsiComment |
               _: PsiWhiteSpace => None
          case _: ScFunctionDefinition |
               _: ScPatternDefinition => None
          case _ => Some(SbtBundle.message("sbt.annotation.sbtFileMustContainOnlyExpressions"))
        }
      } holder.createErrorAnnotation(child, message)
    case _ =>
  }
}

object SbtAnnotator {

  def isTypeAllowed(
    expression: ScExpression,
    expressionType: ScType,
    allowedTypes: String*
  ): Boolean = {
    implicit val context: Context = Context(expression)

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
}
