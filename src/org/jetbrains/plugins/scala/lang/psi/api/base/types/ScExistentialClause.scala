package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package types

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaration

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScExistentialClause extends ScalaPsiElement {
  def declarations : Seq[ScDeclaration] = collection.immutable.Seq(findChildrenByClassScala(classOf[ScDeclaration]).toSeq: _*)
}