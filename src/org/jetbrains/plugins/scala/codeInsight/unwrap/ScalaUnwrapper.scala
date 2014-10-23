package org.jetbrains.plugins.scala
package codeInsight.unwrap

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.unwrap.AbstractUnwrapper
import com.intellij.psi.PsiElement

/**
 * Nikolay.Tropin
 * 2014-06-26
 */
abstract class ScalaUnwrapper extends AbstractUnwrapper[ScalaUnwrapContext]("") {
  override def createContext() = new ScalaUnwrapContext
}

trait ShortTextDescription {
  this: ScalaUnwrapper =>

  protected def shortText(element: PsiElement) = {
    val text = element.getText
    if (text.length > 20) text.substring(0, 17) + "..." else text
  }

  override def getDescription(e: PsiElement) = CodeInsightBundle.message("unwrap.with.placeholder", shortText(e))
}
