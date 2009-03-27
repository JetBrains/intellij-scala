package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic

import com.intellij.openapi.util.TextRange
import com.intellij.psi.impl.light.LightIdentifier
import com.intellij.psi.{PsiElement, PsiFile}

class JavaIdentifier(scalaId : PsiElement) extends LightIdentifier(scalaId.getManager, scalaId.getText) {
  override def getTextRange = scalaId.getTextRange

  override def getContainingFile = scalaId.getContainingFile
}