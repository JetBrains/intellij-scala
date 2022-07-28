package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.template.Result
import com.intellij.openapi.editor.Document
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

case class ScalaTypeResult(`type`: ScType) extends Result {

  //TODO maybe add a more meaningful implementation
  override def equalsToText(text: String, context: PsiElement): Boolean =
    text == toString

  override def toString: String = `type`.canonicalText

  override def handleFocused(psiFile: PsiFile,
                             document: Document,
                             segmentStart: Int,
                             segmentEnd: Int): Unit = {}
}
