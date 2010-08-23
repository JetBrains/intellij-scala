package org.jetbrains.plugins.scala
package codeInspection

import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaRecursiveElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, Any, AnyVal}
import com.intellij.codeInspection._
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScMethodCall, ScInfixExpr, ScExpression}

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
 *   111111
 *   {
 *     sd
 *   }
 * }
 </pre>
 */
class SuspiciousNewLineInMethodCall extends LocalInspectionTool {
  def getGroupDisplayName: String = InspectionsUtil.SCALA

  def getDisplayName: String = "Suspicious New Line in Method Call"

  def getShortName: String = getDisplayName

  override def isEnabledByDefault: Boolean = false

  override def getStaticDescription: String = "Detects new-lines in method calls that are not inferred as semi-colons"

  override def getID: String = getShortName

  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    if (!file.isInstanceOf[ScalaFile]) return Array[ProblemDescriptor]()

    val scalaFile = file.asInstanceOf[ScalaFile]
    val res = new ArrayBuffer[ProblemDescriptor]

    def addError(place: PsiElement) = {
      res += manager.createProblemDescriptor(place, ScalaBundle.message("suspicicious.newline"),
        Array[LocalQuickFix](), ProblemHighlightType.INFO)
    }

    val visitor = new ScalaRecursiveElementVisitor {
      override def visitElement(elem: ScalaPsiElement): Unit = {
        elem match {
          case x: ScInfixExpr if {
            val es = ScalaPsiUtil.getElementsRange(x.operation, x.rOp)
            val whitespace = es.drop(1).dropRight(1)
            whitespace.exists(_.getText.contains("\n"))
          } => addError(x)
          case mc: ScArgumentExprList if {
            val prev = mc.exprs.headOption.flatMap(first => Option(first.getPrevSibling))
            prev.exists(_.getText.contains("\n"))
          } => addError(mc.getContext)
          case _ =>
        }
        super.visitElement(elem)
      }
    }
    file.accept(visitor)
    return res.toArray
  }
}