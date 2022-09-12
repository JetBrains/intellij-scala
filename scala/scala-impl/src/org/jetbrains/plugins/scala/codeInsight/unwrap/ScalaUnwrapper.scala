package org.jetbrains.plugins.scala.codeInsight.unwrap

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.unwrap.AbstractUnwrapper
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.StringExt

abstract class ScalaUnwrapper extends AbstractUnwrapper[ScalaUnwrapContext]("") {
  override def createContext() = new ScalaUnwrapContext
}

trait ShortTextDescription {
  this: ScalaUnwrapper =>

  protected def shortText(element: PsiElement): String =
    element.getText.shorten(20)

  override def getDescription(e: PsiElement): String =
    CodeInsightBundle.message("unwrap.with.placeholder", shortText(e))
}
