package org.jetbrains.plugins.scala.lang.psi.impl.base

import api.base.{ScFieldId, ScIdList}
import com.intellij.lang.ASTNode
import com.intellij.util.ArrayFactory
import parser.ScalaElementTypes
import psi.stubs.{ScIdListStub}

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScIdListImpl private () extends ScalaStubBasedElementImpl[ScIdList] with ScIdList {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScIdListStub) = {this(); setStub(stub); setNode(null)}

  def fieldIds: Seq[ScFieldId]  = {
    val stub = getStub
    if (stub != null) {
      Seq(stub.getChildrenByType(ScalaElementTypes.FIELD_ID, new ArrayFactory[ScFieldId] {
        def create(count: Int): Array[ScFieldId] = new Array[ScFieldId](count)
      }): _*)
    } else
      Seq(findChildrenByClass(classOf[ScFieldId]): _*)
  }

  override def toString: String = "ListOfIdentifiers"
}