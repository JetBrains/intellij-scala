package org.jetbrains.plugins.scala
package lang.refactoring.rename

import java.util

import com.intellij.psi.search.SearchScope
import com.intellij.psi.{PsiElement, PsiReference}
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValue, ScVariable}

/**
 * Nikolay.Tropin
 * 1/20/14
 */

class RenameScalaBindingPatternProcessor extends RenamePsiElementProcessor with ScalaRenameProcessor {
  override def canProcessElement(element: PsiElement): Boolean = element match {
    case pattern: ScBindingPattern =>
      ScalaPsiUtil.nameContext(pattern) match {
        case _: ScVariable | _: ScValue | _: ScClassParameter => false //handled by RenameScalaValsProcessor
        case _ => true
      }
    case _ => false
  }
}
