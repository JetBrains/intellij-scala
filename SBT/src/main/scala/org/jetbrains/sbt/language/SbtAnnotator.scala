package org.jetbrains.sbt
package language

import com.intellij.lang.annotation.{AnnotationHolder, Annotator}
import com.intellij.psi.{PsiWhiteSpace, PsiComment, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt

/**
 * @author Pavel Fatin
 */
class SbtAnnotator extends Annotator {
  def annotate(element: PsiElement, holder: AnnotationHolder) {
    element match {
      case file: SbtFileImpl =>
        val children = file.children.toVector
        checkElements(children, holder)
        checkBlankLines(children, holder)
      case _ =>
    }
  }

  private def checkElements(children: Seq[PsiElement], holder: AnnotationHolder) {
    children.foreach {
      case _: SbtFileImpl | _: ScImportStmt | _: PsiComment | _: PsiWhiteSpace =>
      case exp: ScExpression => checkExpressionType(exp, holder)
      case other => holder.createErrorAnnotation(other, "SBT file must contain only expressions")
    }
  }

  private def checkExpressionType(exp: ScExpression, holder: AnnotationHolder) {
    exp.getType(TypingContext.empty).foreach { expressionType =>
      if (expressionType.equiv(types.Nothing) || expressionType.equiv(types.Null)) {
        holder.createErrorAnnotation(exp, "Expected expression type is Setting[_] in SBT file")
      } else {
        findType(exp, SbtAnnotator.SbtSettingType).foreach { settingType =>
          if (!expressionType.conforms(settingType)) {
            holder.createErrorAnnotation(exp, s"Expression type ($expressionType) must conform to Setting[_] in SBT file")
          }
        }
      }
    }
  }

  private def findType(exp: ScExpression, text: String) = {
    Option(ScalaPsiElementFactory.createTypeFromText(text, exp.getContext, exp))
  }

  private def checkBlankLines(children: Seq[PsiElement], holder: AnnotationHolder) {
    children.sliding(3).foreach {
      case Seq(_: ScExpression, space: PsiWhiteSpace, e: ScExpression) if (space.getText.count(_ == '\n') == 1) =>
        holder.createErrorAnnotation(e, "Blank line required to separate expressions in SBT file")
      case _ =>
    }
  }
}

object SbtAnnotator {
  val SbtSettingType = "Project.Setting[_]"
}
