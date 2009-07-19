package org.jetbrains.plugins.scala.lang.psi.impl.base

import com.intellij.lang.ASTNode
import psi.stubs.{ScIdListStub}

import org.jetbrains.plugins.scala.lang.psi.api.base._
/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScIdListImpl private () extends ScalaStubBasedElementImpl[ScIdList] with ScIdList{
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScIdListStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "ListOfIdentifiers"
}