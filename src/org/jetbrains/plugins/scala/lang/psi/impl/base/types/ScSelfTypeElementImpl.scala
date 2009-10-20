package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import psi.stubs.ScSelfTypeElementStub
import psi.types.result.{Success, TypingContext}

import org.jetbrains.plugins.scala.lang.psi.api.base.types._

/** 
* @author Alexander Podkhalyuzin
*/

class ScSelfTypeElementImpl extends ScalaStubBasedElementImpl[ScSelfTypeElement] with ScSelfTypeElement{
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScSelfTypeElementStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "SelfType"

  def nameId() = findChildByType(TokenSets.SELF_TYPE_ID)

  def getType(ctx: TypingContext) = Success(calcType, Some(this))
}