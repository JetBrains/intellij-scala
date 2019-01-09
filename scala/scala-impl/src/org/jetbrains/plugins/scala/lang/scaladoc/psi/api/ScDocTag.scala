package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package api


import _root_.org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.javadoc.PsiDocTag

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */

trait ScDocTag extends ScalaPsiElement with PsiDocTag {
  def getCommentDataText(): String
  
  def getAllText(handler: PsiElement => String): String
  def getAllText: String = getAllText(element => element.getText)
}