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
  def getGroupDisplayName: String = InspectionsUtil.SCALA

  def getDisplayName: String = "Case Class Parameter"

  def getShortName: String = "Case Class Param"

  override def isEnabledByDefault: Boolean = true

  override def getStaticDescription: String = "Inspection for redundant 'val' modifier on case class parameters"

  override def getID: String = "CaseClassParam"

  /*override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    if (!file.isInstanceOf[ScalaFile]) return Array[ProblemDescriptor]()
    val scalaFile = file.asInstanceOf[ScalaFile]
    val res = new ArrayBuffer[ProblemDescriptor]

    val visitor = new ScalaRecursiveElementVisitor {
      override def visitTypeDefintion(typedef: ScTypeDefinition) = {
        typedef match {
          case c: ScClass if c.isCase =>
            for{
              paramClause <- c.allClauses.take(1)
              classParam@(__ : ScClassParameter) <- paramClause.parameters
              if classParam.isVal && Option(classParam.getModifierList).map(l => !l.hasExplicitModifiers).getOrElse(true)
            } {
              res += manager.createProblemDescriptor(classParam, ScalaBundle.message("val.on.case.class.param.redundant"),
                Array[LocalQuickFix](new RemoveValQuickFix(classParam)), ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
            }
          case _ =>
        }
        super.visitTypeDefintion(typedef)
      }

    }
    file.accept(visitor)
    return res.toArray
  }*/

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitTypeDefintion(typedef: ScTypeDefinition) = {
        typedef match {
          case c: ScClass if c.isCase =>
            for{
              paramClause <- c.allClauses.take(1)
              classParam@(__ : ScClassParameter) <- paramClause.parameters
              if classParam.isVal && Option(classParam.getModifierList).map(l => !l.hasExplicitModifiers).getOrElse(true)
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