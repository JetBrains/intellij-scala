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

trait ScTypes extends ScalaPsiElement {
  def types: Seq[ScTypeElement] = collection.immutable.Sequence(findChildrenByClassScala(classOf[ScTypeElement]).toSeq: _*)
}