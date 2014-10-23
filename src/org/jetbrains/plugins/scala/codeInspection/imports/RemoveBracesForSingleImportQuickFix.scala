package org.jetbrains.plugins.scala
package codeInspection.imports

import com.intellij.codeInspection.{LocalQuickFix, ProblemDescriptor}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * @author Ksenia.Sautina
 * @since 4/11/12
 */

class RemoveBracesForSingleImportQuickFix(importExpr: ScImportExpr) extends LocalQuickFix {
  def applyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!importExpr.isValid) return

    val buf = new StringBuilder
    buf.append(importExpr.qualifier.getText).append(".")

    if (importExpr.singleWildcard) {
     buf.append("_")
    } else {
     buf.append(importExpr.getNames(0))
    }

    val newImportExpr = ScalaPsiElementFactory.createImportExprFromText(buf.toString(), importExpr.getManager)

    inWriteAction {
      importExpr.replace(newImportExpr)
    }
  }

  def getName: String = "Remove braces from import statement"

  def getFamilyName: String = "Remove braces from import statement"
}
