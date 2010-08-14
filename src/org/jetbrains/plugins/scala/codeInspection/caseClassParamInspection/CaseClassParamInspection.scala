package org.jetbrains.plugins.scala
package codeInspection
package caseClassParamInspection

import collection.mutable.ArrayBuffer
import com.intellij.codeInspection._
import com.intellij.psi.PsiFile
import java.lang.String
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaRecursiveElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter

/**
 * User: Alexander Podkhalyuzin
 * Date: 02.07.2009
 */

class CaseClassParamInspection extends LocalInspectionTool {
  def getGroupDisplayName: String = InspectionsUtil.SCALA

  def getDisplayName: String = "Case Class Parameter Inspection"

  def getShortName: String = "Case Class Param"

  override def isEnabledByDefault: Boolean = true

  override def getStaticDescription: String = "Inspection for redundant 'val' modifier on case class parameters"

  override def getID: String = "Case Class Param"

  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
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
  }
}