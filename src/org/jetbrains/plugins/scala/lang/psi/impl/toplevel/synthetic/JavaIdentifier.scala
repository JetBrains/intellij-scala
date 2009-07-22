package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic

import com.intellij.openapi.util.TextRange
import com.intellij.psi.impl.light.LightIdentifier
import com.intellij.psi.{PsiElement, PsiFile}
import java.lang.String

class JavaIdentifier(scalaId : PsiElement) extends LightIdentifier(scalaId.getManager, scalaId.getText) {
  override def getTextRange = scalaId.getTextRange

  override def getStartOffsetInParent: Int = scalaId.getStartOffsetInParent

  override def getTextOffset: Int = scalaId.getTextOffset

  override def getContainingFile = scalaId.getContainingFile
}