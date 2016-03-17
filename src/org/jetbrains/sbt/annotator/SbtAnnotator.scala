package org.jetbrains.sbt
package annotator

import com.intellij.lang.annotation.{AnnotationHolder, Annotator}
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiComment, PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt, api}
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

  private class Worker(sbtFileElements: Seq[PsiElement], sbtVersion: String, holder: AnnotationHolder) {
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

    private def annotateTypeMismatch(expression: ScExpression)
                                    (implicit typeSystem: api.TypeSystem): Unit =
      expression.getType(TypingContext.empty).foreach { expressionType =>
        if (expressionType.equiv(types.Nothing) || expressionType.equiv(types.Null)) {
          holder.createErrorAnnotation(expression, SbtBundle("sbt.annotation.expectedExpressionType"))
        } else {
          if (!isTypeAllowed(expression, expressionType))
            holder.createErrorAnnotation(expression, SbtBundle("sbt.annotation.expressionMustConform", expressionType))
        }
      }

    private def findTypeByText(exp: ScExpression, text: String): Option[ScType] =
      Option(ScalaPsiElementFactory.createTypeFromText(text, exp.getContext, exp))

    private def isTypeAllowed(expression: ScExpression, expressionType: ScType): Boolean =
      SbtAnnotator.AllowedTypes.exists(typeStr => findTypeByText(expression, typeStr) exists (t => expressionType conforms t))

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
  val AllowedTypes = List("Seq[Def.SettingsDefinition]", "Def.SettingsDefinition")
}
