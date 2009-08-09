package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import com.intellij.psi.tree.IElementType
import lexer.ScalaTokenTypes
import psi.ScalaPsiElement
import com.intellij.psi.PsiModifierList

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScModifierList extends ScalaPsiElement with PsiModifierList {
  def has(prop : IElementType) : Boolean

  //only one access modifier can occur in a particular modifier list
  def accessModifier = findChild(classOf[ScAccessModifier])

  def getModifiersStrings: Array[String]
}