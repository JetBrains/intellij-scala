package org.jetbrains.plugins.scala
package codeInsight.unwrap

import java.util

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody

/**
 * Nikolay.Tropin
 * 2014-06-30
 */
class ScalaBracesUnwrapper extends ScalaUnwrapper {

  override def isApplicableTo(e: PsiElement) = e match {
    case b: ScBlock if b.hasRBrace && canBeUnwrapped(b) => true
    case _ => false
  }

  override def doUnwrap(element: PsiElement, context: ScalaUnwrapContext) = element match {
    case b: ScBlock if b.hasRBrace && canBeUnwrapped(b) =>
      context.extractBlockOrSingleStatement(b, b)
      context.delete(b)
    case _ =>
  }

  override def getDescription(e: PsiElement) = CodeInsightBundle.message("unwrap.braces")

  override def collectAffectedElements(e: PsiElement, toExtract: util.List[PsiElement]) = e match {
    case b: ScBlock if b.hasRBrace && canBeUnwrapped(b) =>
      super.collectAffectedElements(e, toExtract)
      b.getParent
    case _ => e
  }

  private def canBeUnwrapped(block: ScBlock): Boolean = block.getParent match {
    case _: ScBlock | _: ScTemplateBody => true
    case _ => false
  }
}
