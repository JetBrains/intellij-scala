package org.jetbrains.plugins.scala
package codeInspection
package varCouldBeValInspection

import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.{PsiElementVisitor, PsiReference}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import com.intellij.psi.search.SearchScope
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScReferenceExpression, ScAssignStmt, ScInfixExpr}
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

class VarCouldBeValInspection extends LocalInspectionTool {
  def getGroupDisplayName: String = InspectionsUtil.SCALA

  def getDisplayName: String = "'var' could be a 'val'"

  def getShortName: String = getDisplayName

  override def isEnabledByDefault: Boolean = true

  override def getStaticDescription: String = "Detects local 'var'-s that are never assigned to."

  override def getID: String = "VarCouldBeVal"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    if (!holder.getFile.isInstanceOf[ScalaFile]) return new PsiElementVisitor {}
    def addError(varDef: ScVariableDefinition) = holder.registerProblem(holder.getManager.createProblemDescriptor(varDef, "'var' could be a 'val'",
                    Array[LocalQuickFix](new VarToValQuickFix(varDef)), ProblemHighlightType.INFO))

    new ScalaElementVisitor {
      override def visitElement(elem: ScalaPsiElement) {
        elem match {
          case x: ScVariableDefinition =>
            x.contexts.take(1).toList match {
              case (_: ScTemplateBody) :: _ => // ignore members, just local vars.
              case _ =>
                //15% faster then previous, more functional approach
                var assigns = false
                val decElemIterator = x.declaredElements.iterator
                while (decElemIterator.hasNext && !assigns) {
                  val decElem = decElemIterator.next
                  val usageIterator = ReferencesSearch.search(decElem, decElem.getUseScope).iterator
                  while (usageIterator.hasNext && !assigns) {
                    val usage = usageIterator.next
                    if (isAssignment(usage)) assigns = true
                  }
                }
                if (!assigns) addError(x)
            }
          case _ =>
        }
        super.visitElement(elem)
      }
    }
  }

  // This is a conservative approximation, we should really resolve the operation
  // to differentiate self assignment from calling a method whose name happends to be an assignment operator.
  private def isAssignment(ref: PsiReference): Boolean = ref.getElement.getContext match {
    case assign: ScAssignStmt if assign.getLExpression == ref.getElement => true
    case infix: ScInfixExpr if infix.isAssignmentOperator => true
    case ref1 @ ScReferenceExpression.qualifier(`ref`) => ParserUtils.isAssignmentOperator(ref1.refName)
    case _ => false
  }
}

// TODO Test
//  def method {
//    val a = 1
//    a.+=(2) // re-assignment to val
//    a += 2  // re-assignment to val
//
//    var b = 1
//    b.+=(2)
//
//    var c = 1
//    c += 2
//
//    var d = 1 // var could be val
//    d + 1
//  }
