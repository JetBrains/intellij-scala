package org.jetbrains.plugins.scala.lang.psi.api.base

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScAccessModifier extends ScalaPsiElement {
  def scope() : PsiNamedElement /* either ScTypeDefinition or PsiPackage */

  type AccessType = Access.Value

  def access() : AccessType 

  object Access extends Enumeration {
    val PRIVATE, PROTECTED, THIS_PRIVATE, THIS_PROTECTED = Value
  }
}