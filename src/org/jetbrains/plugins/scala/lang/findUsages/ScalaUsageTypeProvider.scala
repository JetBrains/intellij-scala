package org.jetbrains.plugins.scala
package lang
package findUsages

import com.intellij.psi.PsiElement
import psi.api.toplevel.imports.ScImportExpr
import com.intellij.usages.impl.rules.{UsageType, UsageTypeProvider}
import org.jetbrains.plugins.scala.extensions._

final class ScalaUsageTypeProvider extends UsageTypeProvider {
  def getUsageType(element: PsiElement) = {
    element.containingScalaFile match {
      case Some(_) =>
        element match {
        case x if x.parentsInFile.findByType(classOf[ScImportExpr]).isDefined => UsageType.CLASS_IMPORT
        case _ => null
      }
      case None => null
    }
  }
}
