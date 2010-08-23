package org.jetbrains.plugins.scala
package codeInspection
package varCouldBeValInspection

import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaRecursiveElementVisitor, ScalaFile}
import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.{PsiReference, PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignStmt, ScInfixExpr}

class VarCouldBeValInspection extends LocalInspectionTool {
  def getGroupDisplayName: String = InspectionsUtil.SCALA

  def getDisplayName: String = "'var' could be a 'val'"

  def getShortName: String = getDisplayName

  override def isEnabledByDefault: Boolean = true

  override def getStaticDescription: String = "Detects local 'var'-s that are never assigned to."

  override def getID: String = getShortName

  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    if (!file.isInstanceOf[ScalaFile]) return Array[ProblemDescriptor]()

    val scalaFile = file.asInstanceOf[ScalaFile]
    val res = new ArrayBuffer[ProblemDescriptor]

    def addError(varDef: ScVariableDefinition) = res += manager.createProblemDescriptor(varDef, "'var' could be a 'val'",
                    Array[LocalQuickFix](new ValToVarQuickFix(varDef)), ProblemHighlightType.INFO)

    val visitor = new ScalaRecursiveElementVisitor {
      override def visitElement(elem: ScalaPsiElement) {
        elem match {
          case x: ScVariableDefinition =>
            x.contexts.take(1).toList match {
              case (x: ScTemplateDefinition) :: _ => // ignore members, just local vars.
              case _ =>
                import collection.JavaConversions._
                val assigns = for{
                  decElem <- x.declaredElements.iterator
                  usage <- ReferencesSearch.search(decElem).iterator
                  if isAssignment(usage)
                } yield usage
                if (assigns isEmpty) addError(x)
            }
          case _ =>
        }
        super.visitElement(elem)
      }
    }
    file.accept(visitor)
    return res.toArray
  }

  private def isAssignment(ref: PsiReference): Boolean = ref.getElement.getContext match {
    case assign: ScAssignStmt if assign.getLExpression == ref.getElement => true
    // This is a conservative approximation, we should really resolve the operation
    // to differentiate self assignment from calling a method whose name happends to be an assignment operator.
    case infix: ScInfixExpr if infix.isAssignmentOperator => true
    case _ => false
  }
}