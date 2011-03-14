package org.jetbrains.plugins.scala
package lang
package findUsages

import psi.api.toplevel.imports.ScImportExpr
import com.intellij.usages.Usage
import com.intellij.psi.{PsiFile, PsiJavaFile, PsiImportList, PsiElement}
import com.intellij.usages.rules.{PsiElementUsage, ImportFilteringRule}
import psi.api.ScalaFile
import org.jetbrains.plugins.scala.extensions._

final class ScalaImportFilteringRule extends ImportFilteringRule {

  def isVisible(usage: Usage) = usage match {
    case p: PsiElementUsage =>
      val element: PsiElement = p.getElement
      element.containingScalaFile match {
        case Some(_) =>
          val isImport = element.parentsInFile.findByType(classOf[ScImportExpr]).isDefined
          !isImport
        case None => true
      }
    case _ => true
  }
}