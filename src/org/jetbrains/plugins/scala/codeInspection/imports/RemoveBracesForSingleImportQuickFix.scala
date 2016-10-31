package org.jetbrains.plugins.scala
package codeInspection.imports

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createImportExprFromText

/**
 * @author Ksenia.Sautina
 * @since 4/11/12
 */

class RemoveBracesForSingleImportQuickFix(importExpr: ScImportExpr)
        extends AbstractFixOnPsiElement(ScalaBundle.message("remove.braces.from.import"), importExpr) {
  def doApplyFix(project: Project) {
    val iExpr = getElement
    if (!iExpr.isValid) return

    val name = if (iExpr.isSingleWildcard) "_" else iExpr.getNames(0)
    val text = s"${iExpr.qualifier.getText}.$name"

    inWriteAction {
      iExpr.replace(createImportExprFromText(text)(iExpr.getManager))
    }
  }
}
