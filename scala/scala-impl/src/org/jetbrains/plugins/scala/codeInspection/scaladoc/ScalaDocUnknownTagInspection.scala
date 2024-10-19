package org.jetbrains.plugins.scala.codeInspection.scaladoc

import com.intellij.codeInspection._
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocTag

final class ScalaDocUnknownTagInspection extends LocalInspectionTool with DumbAware {
  override def isEnabledByDefault: Boolean = true

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitTag(s: ScDocTag): Unit = {
        val tagNameElement = s.getNameElement
        assert(tagNameElement != null)
        assert(tagNameElement.getNode.getElementType == ScalaDocTokenType.DOC_TAG_NAME)

        if (!MyScaladocParsing.allTags.contains(tagNameElement.getText)) {
          holder.registerProblem(holder.getManager.createProblemDescriptor(tagNameElement, getDisplayName, true,
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly, new ScalaDocDeleteUnknownTagInspection(s)))
        } else {
          val condition = MyScaladocParsing.tagsWithParameters.contains(tagNameElement.getText) &&
            (tagNameElement.getNextSibling.getNextSibling == null ||
              tagNameElement.getNextSibling.getNextSibling.getNode.getElementType != ScalaDocTokenType.DOC_TAG_VALUE_TOKEN)
          if (condition) {
            holder.registerProblem(holder.getManager.createProblemDescriptor(
              tagNameElement,
              ScalaInspectionBundle.message("missing.tag.parameter"), true,
              ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly
            ))
          }
        }
      }
    }
  }

  override def getDisplayName: String = ScalaInspectionBundle.message("unknown.scaladoc.tag")
}

final class ScalaDocDeleteUnknownTagInspection(unknownTag: ScDocTag)
  extends AbstractFixOnPsiElement(ScalaBundle.message("delete.unknown.tag"), unknownTag)
    with DumbAware {
  override def getFamilyName: String = FamilyName

  override protected def doApplyFix(tag: ScDocTag)(implicit project: Project): Unit =
    tag.delete()
}
