package org.jetbrains.plugins.scala
package lang.refactoring.rename

import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.psi.{PsiReference, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import java.util

/**
 * Nikolay.Tropin
 * 1/20/14
 */

class RenameScalaBindingPatternProcessor extends RenamePsiElementProcessor {
  def canProcessElement(element: PsiElement): Boolean = element match {
    case pattern: ScBindingPattern =>
      ScalaPsiUtil.nameContext(pattern) match {
        case _: ScVariable | _: ScValue | _: ScClassParameter => false //handled by RenameScalaValsProcessor
        case _ => true
      }
    case _ => false
  }

  override def findReferences(element: PsiElement): util.Collection[PsiReference] = ScalaRenameUtil.findReferences(element)
}
