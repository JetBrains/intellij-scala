package org.jetbrains.plugins.scala.lang.scaladoc.psi.api

import com.intellij.psi.PsiElement
import com.intellij.psi.javadoc.PsiDocTag
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

trait ScDocTag extends ScalaPsiElement with PsiDocTag {
  def getCommentDataText: String // TODO: unused
  def getAllText(handler: PsiElement => String): String
  def getAllText: String = getAllText(element => element.getText)
}