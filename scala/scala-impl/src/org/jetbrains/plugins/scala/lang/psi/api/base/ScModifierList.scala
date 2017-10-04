package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import com.intellij.psi.PsiModifierList
import com.intellij.psi.tree.IElementType

/**
  * @author Alexander Podkhalyuzin
  *         Date: 22.02.2008
  */

trait ScModifierList extends ScalaPsiElement with PsiModifierList {
  def has(prop: IElementType): Boolean

  //only one access modifier can occur in a particular modifier list
  def accessModifier: Option[ScAccessModifier]

  def modifiers: Array[String]

  def hasExplicitModifiers: Boolean
}