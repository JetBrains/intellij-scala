package org.jetbrains.plugins.scala
package codeInspection

import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaRecursiveElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, Any, AnyVal}
import com.intellij.codeInspection._
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
 * Detects method calls with suspicuous new-lines:
 *
 * <pre>
 * {
 *   object test {
 *     def foo(implicit a: Any) = 1
 *   }
 *
 *   test foo
 *   1
 *
 *   1
 *   {
 *     foo
 *   }
 * }
 </pre>
 */
class SuspiciousNewLineInMethodCall extends LocalInspectionTool {
  import SuspiciousNewLineInMethodCall._
  
  def getGroupDisplayName: String = InspectionsUtil.SCALA

  def getDisplayName: String = "Suspicious New Line in Method Call"

  def getShortName: String = getDisplayName

  override def isEnabledByDefault: Boolean = true

  override def getStaticDescription: String = "Detects new-lines in method calls that are not inferred as semi-colons"

  override def getID: String = "SuspiciousNewLineInMethodCall"

  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    def createDescriptor(p: Problem) = manager.createProblemDescriptor(p.place, p.message, Array[LocalQuickFix](), ProblemHighlightType.INFO)
    checkFileInternal(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean).map(createDescriptor).toArray
  }

  // For unit testing. When trying to test through the method above, I got the error: "ERROR: Non-physical PsiElement. Physical element is required to be able to anchor the problem"
  private[codeInspection] def checkFileInternal(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Seq[Problem] = {
    if (!file.isInstanceOf[ScalaFile]) return Seq()

    val scalaFile = file.asInstanceOf[ScalaFile]
    val res = new ArrayBuffer[Problem]

    def addError(place: PsiElement) = {
      res += Problem(place, ScalaBundle.message("suspicicious.newline"))
    }

    def newLineBetweenOperationAndROp(x: ScInfixExpr) = {
      val es = ScalaPsiUtil.getElementsRange(x.operation, x.rOp)
      val whitespace = es.drop(1).dropRight(1)
      whitespace.exists(_.getText.contains("\n"))
    }

    val visitor = new ScalaRecursiveElementVisitor {
      override def visitElement(elem: ScalaPsiElement): Unit = {
        elem match {
          case x: ScInfixExpr if !elem.getContext.isInstanceOf[ScParenthesisedExpr] && newLineBetweenOperationAndROp(x) => addError(x)
          case mc: ScArgumentExprList if mc.getText.startsWith("\n") => addError(mc.getContext)
          case _ =>
        }
        super.visitElement(elem)
      }
    }
    file.accept(visitor)
    return res.toSeq
  }
}

object SuspiciousNewLineInMethodCall {
  case class Problem(place: PsiElement, message: String)
}