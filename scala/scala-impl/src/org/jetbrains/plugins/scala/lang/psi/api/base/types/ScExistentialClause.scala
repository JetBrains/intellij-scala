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

trait ScExistentialClauseBase extends ScalaPsiElementBase { this: ScExistentialClause =>
  def declarations : Seq[ScDeclaration] = findChildren[ScDeclaration]
}