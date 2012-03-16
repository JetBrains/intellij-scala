package org.jetbrains.plugins.scala
package codeInspection
package caseClassParamInspection

import collection.mutable.ArrayBuffer
import com.intellij.codeInspection._
import java.lang.String
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import com.intellij.psi.{PsiElementVisitor, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaRecursiveElementVisitor, ScalaFile}


class CaseClassParamInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean = true

  override def getID: String = "CaseClassParam"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitTypeDefintion(typedef: ScTypeDefinition) = {
        typedef match {
          case c: ScClass if c.isCase =>
            for{
              paramClause <- c.allClauses.take(1)
              classParam@(__ : ScClassParameter) <- paramClause.parameters
              if classParam.isVal && classParam.isCaseClassVal
            } {
              holder.registerProblem(holder.getManager.createProblemDescriptor(classParam,
                ScalaBundle.message("val.on.case.class.param.redundant"),
                Array[LocalQuickFix](new RemoveValQuickFix(classParam)), ProblemHighlightType.GENERIC_ERROR_OR_WARNING))
            }
          case _ =>
        }
        super.visitTypeDefintion(typedef)
      }
    }
  }
}