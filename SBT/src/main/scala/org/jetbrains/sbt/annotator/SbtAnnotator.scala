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
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.sbt.language.SbtFileImpl
import org.jetbrains.sbt.project.settings.SbtSettings

/**
 * @author Pavel Fatin
 */
class SbtAnnotator extends Annotator {
  def annotate(element: PsiElement, holder: AnnotationHolder) {
    element match {
      case file: SbtFileImpl =>
        val children = file.children.toVector
        checkElements(children, holder)
        val sbtVersion = SbtSettings.getInstance(element.getProject).sbtVersion
        if (StringUtil.compareVersionNumbers(sbtVersion, "0.13.7") < 0)
          checkBlankLines(children, holder)
      case _ =>
    }
  }

  private def checkElements(children: Seq[PsiElement], holder: AnnotationHolder) {
    if (children.isEmpty) return 
    
    val is13_+ = {
      val sbtVersion = SbtSettings.getInstance(children.head.getProject).sbtVersion
      StringUtil.compareVersionNumbers(sbtVersion, "0.13.0") >= 0
    }

    children.foreach {
      case _: SbtFileImpl | _: ScImportStmt | _: PsiComment | _: PsiWhiteSpace =>
      case exp: ScExpression => checkExpressionType(exp, holder)
      case _: ScFunctionDefinition | _: ScPatternDefinition if is13_+ =>
      case other => holder.createErrorAnnotation(other, "SBT file must contain only expressions")
    }
  }

  private def checkExpressionType(exp: ScExpression, holder: AnnotationHolder) {
    exp.getType(TypingContext.empty).foreach { expressionType =>
      if (expressionType.equiv(types.Nothing) || expressionType.equiv(types.Null)) {
        holder.createErrorAnnotation(exp, "Expected expression type is Setting[_] in SBT file")
      } else {
        if (!checkType(exp, expressionType)) {
          holder.createErrorAnnotation(exp, s"Expression type ($expressionType) must conform to Setting[_] in SBT file")
        }
      }
    }
  }

  private def findType(exp: ScExpression, text: String) = {
    Option(ScalaPsiElementFactory.createTypeFromText(text, exp.getContext, exp))
  }
  
  private def checkType(exp: ScExpression, tpe: ScType) = {
    SbtAnnotator.allTypes exists {
      case expected => findType(exp, expected) exists (a => tpe conforms a)
    }
  }

  private def checkBlankLines(children: Seq[PsiElement], holder: AnnotationHolder) {
    children.sliding(3).foreach {
      case Seq(_: ScExpression, space: PsiWhiteSpace, e: ScExpression) if space.getText.count(_ == '\n') == 1 =>
        holder.createErrorAnnotation(e, "Blank line required to separate expressions in SBT file")
      case _ =>
    }
  }
}

object SbtAnnotator {
  val SbtSettingType = "Project.Setting[_]"
  val SbtSettingSeqType = "Seq[Project.Setting[_]]"
  
  val allTypes = List(SbtSettingSeqType, SbtSettingType)
}
