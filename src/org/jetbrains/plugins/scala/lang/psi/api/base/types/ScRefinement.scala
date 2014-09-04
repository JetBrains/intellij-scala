package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScTypeAlias}

/**
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScRefinement extends ScalaPsiElement {
  def holders : Seq[ScDeclaredElementsHolder] = collection.immutable.Seq(findChildrenByClassScala(classOf[ScDeclaredElementsHolder]).toSeq: _*)
  def types : Seq[ScTypeAlias] = collection.immutable.Seq(findChildrenByClassScala(classOf[ScTypeAlias]).toSeq: _*)
}