package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScCompoundTypeElement extends ScTypeElement {
  def components : Seq[ScTypeElement] = collection.immutable.Seq(findChildrenByClassScala(classOf[ScTypeElement]).toSeq: _*)
  def refinement = findChild(classOf[ScRefinement])
}