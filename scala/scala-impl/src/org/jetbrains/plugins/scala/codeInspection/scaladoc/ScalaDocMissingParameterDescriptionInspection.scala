package org.jetbrains.plugins.scala
package codeInspection
package scaladoc

import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocTag


/**
 * User: Dmitry Naydanov
 * Date: 12/17/11
 */

class ScalaDocMissingParameterDescriptionInspection extends LocalInspectionTool {

  override def getDisplayName: String = ScalaInspectionBundle.message("display.name.missing.parameter.description")

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitTag(s: ScDocTag): Unit = {
        if (!ScalaDocMissingParameterDescriptionInspection.OurTags.contains(s.name) || s.getValueElement == null) {
          return
        }

        val children = s.findChildrenByType(ScalaDocTokenType.DOC_COMMENT_DATA)
        for (child <- children) {
          if (child.getText.length() > 1 && child.getText.split(" ").nonEmpty) {
            return
          }
        }

        holder.registerProblem(holder.getManager.createProblemDescriptor(
          if (s.getValueElement != null) s.getValueElement else s, getDisplayName, true,
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly))
      }
    }
  }
}


object ScalaDocMissingParameterDescriptionInspection {
  import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing._

  val OurTags = Set(PARAM_TAG, THROWS_TAG, TYPE_PARAM_TAG)
}
