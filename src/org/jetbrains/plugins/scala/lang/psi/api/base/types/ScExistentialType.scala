package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

trait ScExistentialTypeElement extends ScTypeElement {
  def quantified = findChildByClass(classOf[ScTypeElement])
  def clause = findChildByClass(classOf[ScExistentialClause])
}