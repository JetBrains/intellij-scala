package org.jetbrains.plugins.scala
package codeInspection
package scaladoc

import com.intellij.codeInspection._
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocTag

/**
 * User: Dmitry Naydanov
 * Date: 11/21/11
 */
class ScalaDocUnknownTagInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean = true

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitTag(s: ScDocTag) {
        val tagNameElement = s.getFirstChild
        assert(tagNameElement != null)
        assert(tagNameElement.getNode.getElementType == ScalaDocTokenType.DOC_TAG_NAME)
        
        if (!MyScaladocParsing.allTags.contains(tagNameElement.getText)) {
          holder.registerProblem(holder.getManager.createProblemDescriptor(tagNameElement, getDisplayName, true,
            ProblemHighlightType.GENERIC_ERROR, isOnTheFly, new ScalaDocDeleteUnknownTagInspection(s)))
        } else if (MyScaladocParsing.tagsWithParameters.contains(tagNameElement.getText) &&
          (tagNameElement.getNextSibling.getNextSibling == null ||
             tagNameElement.getNextSibling.getNextSibling.getNode.getElementType != ScalaDocTokenType.DOC_TAG_VALUE_TOKEN))
        {
          holder.registerProblem(holder.getManager.createProblemDescriptor(tagNameElement,
            "Missing Tag Parameter", true, ProblemHighlightType.GENERIC_ERROR, isOnTheFly))
        }
      }
    }
  }

  override def getDisplayName: String = "Unknown scaladoc tag"
}


class ScalaDocDeleteUnknownTagInspection(unknownTag: ScDocTag)
        extends AbstractFixOnPsiElement(ScalaBundle.message("delete.unknown.tag"), unknownTag) {

  override def getFamilyName: String = InspectionsUtil.SCALADOC

  override protected def doApplyFix(tag: ScDocTag)
                                   (implicit project: Project): Unit = {
      tag.delete()
  }
}