package org.jetbrains.plugins.scala
package codeInspection
package scaladoc

import com.intellij.codeInspection._
import com.intellij.psi.{PsiElement, PsiElementVisitor, PsiErrorElement}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

/**
 * User: Dmitry Naidanov
 * Date: 11/19/11
 */

class ScalaDocParserErrorInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean = true

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {

    new ScalaElementVisitor {
      override def visitDocComment(s: ScDocComment): Unit = {
        visitScaladocElement(s)
      }

      override def visitScaladocElement(element: ScalaPsiElement): Unit = {
        for (child <- element.getChildren) {
          child match {
            case a: PsiErrorElement => 
              val startElement: PsiElement = if (a.getPrevSibling == null) a else a.getPrevSibling
              val endElement: PsiElement = if (a.getPrevSibling != null) {
                a
              } else if (a.getNextSibling != null) {
                a.getNextSibling
              } else {
                a.getParent
              }
              //noinspection ReferencePassedToNls
              holder.registerProblem(holder.getManager.createProblemDescriptor(startElement, endElement,
                a.getErrorDescription, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly));
            case b: ScalaPsiElement if b.getChildren.nonEmpty => visitScaladocElement(b)
            case _ => //do nothing
          }
        }        
      }
    }
  }
}