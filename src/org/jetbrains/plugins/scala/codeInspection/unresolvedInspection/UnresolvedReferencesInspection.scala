package org.jetbrains.plugins.scala.codeInspection.unresolvedInspection

import java.lang.String
import org.jetbrains.plugins.scala.codeInspection.InspectionsUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import com.intellij.psi.{PsiFile, PsiElementVisitor}
import org.jetbrains.plugins.scala.psi.api.{ScalaRecursiveElementVisitor, ScalaElementVisitor}
import com.intellij.codeInspection._
import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.02.2010
 */

class UnresolvedReferencesInspection extends LocalInspectionTool {
  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    val res = new ArrayBuffer[ProblemDescriptor]
    val visitor = new ScalaRecursiveElementVisitor {
      override def visitReference(ref: ScReferenceElement) {
        val resolve = ref.multiResolve(false)
        if (resolve.length != 1) {
          ref.getParent match {
            case _: ScImportSelector | _: ScImportExpr if resolve.length > 0 =>
            case _ => {
              res += manager.createProblemDescriptor(ref.nameId, "cannot resolve symbol", false, ProblemHighlightType.ERROR)
            }
          }
        }
        super.visitReference(ref)
      }
    }
    file.accept(visitor)
    res.toArray
  }

  def getShortName: String = "Unresolved references"

  def getDisplayName: String = "Unresolved references inspection"

  def getGroupDisplayName: String = InspectionsUtil.SCALA
}