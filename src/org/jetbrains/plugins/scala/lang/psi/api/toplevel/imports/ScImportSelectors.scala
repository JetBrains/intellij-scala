package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package imports

import com.intellij.psi.PsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

trait ScImportSelectors extends ScalaPsiElement {
  def selectors: Array[ScImportSelector]

  def hasWildcard : Boolean

  def wildcardElement: Option[PsiElement]
}