package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import psi.stubs.ScSelfTypeElementStub;
import org.jetbrains.plugins.scala.lang.psi.api.base.types._

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

class ScSelfTypeElementImpl extends ScalaStubBasedElementImpl[ScSelfTypeElement] with ScSelfTypeElement{
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScSelfTypeElementStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "SelfType"

  def nameId() = findChildByType(TokenSets.SELF_TYPE_ID)
}