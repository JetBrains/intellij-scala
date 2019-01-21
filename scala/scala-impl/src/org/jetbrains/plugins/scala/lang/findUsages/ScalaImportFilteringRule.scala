package org.jetbrains.plugins.scala
package lang
package findUsages

import com.intellij.psi.PsiElement
import com.intellij.usages.Usage
import com.intellij.usages.rules.{ImportFilteringRule, PsiElementUsage}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr

final class ScalaImportFilteringRule extends ImportFilteringRule {

  override def isVisible(usage: Usage): Boolean = usage match {
    case p: PsiElementUsage =>
      val element: PsiElement = p.getElement
      element.containingScalaFile match {
        case Some(_) =>
          val isImport = element.parentsInFile.containsInstanceOf[ScImportExpr]
          !isImport
        case None => true
      }
    case _ => true
  }
}