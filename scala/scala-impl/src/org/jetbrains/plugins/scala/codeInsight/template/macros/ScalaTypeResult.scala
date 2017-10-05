package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.template.Result
import com.intellij.openapi.editor.Document
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
 * @author Roman.Shein
 * @since 22.09.2015.
 */
class ScalaTypeResult(val myType: ScType) extends Result {
  override def equalsToText(text: String, context: PsiElement): Boolean = {
    //TODO maybe add a more meaningful implementation
    text == toString
  }

  override def toString: String = myType.canonicalText

  override def handleFocused(psiFile: PsiFile, document: Document, segmentStart: Int, segmentEnd: Int): Unit = {}
}
