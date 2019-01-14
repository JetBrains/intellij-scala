package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.api.Nothing
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
class ScMatchImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScMatch {

  protected override def innerType: TypeResult = {
    val branchesTypes = getBranches.map(_.`type`().getOrNothing)
    val branchesLub = branchesTypes.foldLeft(Nothing: ScType)(_.lub(_))
    Right(branchesLub)
  }

  override def toString: String = "MatchStatement"
}