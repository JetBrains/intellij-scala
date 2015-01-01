package org.jetbrains.plugins.scala
package codeInspection.imports

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * @author Ksenia.Sautina
 * @since 4/11/12
 */

class RemoveBracesForSingleImportQuickFix(importExpr: ScImportExpr)
        extends AbstractFixOnPsiElement(ScalaBundle.message("remove.braces.from.import"), importExpr) {
  def doApplyFix(project: Project) {
    val iExpr = getElement
    if (!iExpr.isValid) return

    val buf = new StringBuilder
    buf.append(iExpr.qualifier.getText).append(".")

    if (iExpr.singleWildcard) {
     buf.append("_")
    } else {
     buf.append(iExpr.getNames(0))
    }

    val newImportExpr = ScalaPsiElementFactory.createImportExprFromText(buf.toString(), iExpr.getManager)

    inWriteAction {
      iExpr.replace(newImportExpr)
    }
  }
}
