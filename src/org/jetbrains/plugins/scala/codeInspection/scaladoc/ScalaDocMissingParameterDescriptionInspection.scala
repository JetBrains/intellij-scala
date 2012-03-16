package org.jetbrains.plugins.scala
package codeInspection
package scaladoc

import java.lang.String
import com.intellij.psi.PsiElementVisitor
import lang.psi.api.ScalaElementVisitor
import lang.scaladoc.psi.api.ScDocTag
import lang.scaladoc.lexer.ScalaDocTokenType
import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder, LocalInspectionTool, LocalInspectionEP}
import extensions.toPsiNamedElementExt


/**
 * User: Dmitry Naydanov
 * Date: 12/17/11
 */

class ScalaDocMissingParameterDescriptionInspection extends LocalInspectionTool {

  override def getDisplayName: String = "Missing Parameter Description"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitTag(s: ScDocTag) {
        if (!ScalaDocMissingParameterDescriptionInspection.OurTags.contains(s.name) || s.getValueElement == null) {
          return
        }

        val children = s.findChildrenByType(ScalaDocTokenType.DOC_COMMENT_DATA)
        for (child <- children) {
          if (child.getText.length() > 1 && child.getText.split(" ").length > 0) {
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
  import lang.scaladoc.parser.parsing.MyScaladocParsing._

  val OurTags = Set(PARAM_TAG, THROWS_TAG, TYPE_PARAM_TAG)
}
