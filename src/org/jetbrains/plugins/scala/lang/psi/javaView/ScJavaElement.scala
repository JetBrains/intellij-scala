package org.jetbrains.plugins.scala.lang.psi.javaView {

import com.intellij.psi.PsiElement
import com.intellij.extapi.psi.PsiElementBase
import com.intellij.lang.Language
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.lang.StdLanguages

/**
 * @author ven
 */
abstract class ScJavaElement(scElement : ScalaPsiElementImpl, parent : PsiElement) extends PsiElementBase with PsiElement {
  def getLanguage : Language = StdLanguages.JAVA

  def getParent : PsiElement = parent

  def getFirstChild : PsiElement = {

    parent.getChildren.length match {
      case 0 => null
      case _ => {
        val children = parent.getChildren
        children(0)
      }
    }

  }

  def getLastChild : PsiElement = {
    parent.getChildren.length match {
      case 0 => null
      case _ => {
        val children = parent.getChildren
        children(children.length - 1)
      }
    }
  }

  def getNextSibling : PsiElement = {
    var prev : PsiElement = null
    for (val child <- parent.getChildren) {
      if (prev == this) return child
      prev = child
    }
    null
  }

  def getPrevSibling : PsiElement = {
    var prev : PsiElement = null
    for (val child <- parent.getChildren) {
      if (child == this) return prev
      prev = child
    }
    null
  }

  def getTextRange : TextRange = scElement.getTextRange

  def getStartOffsetInParent : Int = scElement.getStartOffsetInParent

  def getTextLength : Int = scElement.getTextLength

  def findElementAt(offset : Int) : PsiElement = {
    val offsetInFile = offset + getTextRange.getStartOffset
    for (val child <- getChildren) {
      val textRange = child.getTextRange
      if (textRange.contains(offsetInFile)) {
        return child.findElementAt(offsetInFile - textRange.getStartOffset())
      }
    }
    if (getTextRange.contains(offsetInFile))  this else null
  }

  def getTextOffset : Int = scElement.getTextOffset

  def getText : String = scElement.getText

  def textToCharArray : Array[Char] = scElement.textToCharArray

  def textContains(c : Char) : Boolean = scElement.textContains(c)

  def getNode : ASTNode =  null
}

}