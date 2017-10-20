package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 */

trait ScTypes extends ScalaPsiElement {
  def types: Seq[ScTypeElement] = findChildrenByClassScala(classOf[ScTypeElement]).toSeq
}