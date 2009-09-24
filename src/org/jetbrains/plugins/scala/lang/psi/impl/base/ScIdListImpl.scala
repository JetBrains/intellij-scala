package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

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
      collection.immutable.Sequence(stub.getChildrenByType(ScalaElementTypes.FIELD_ID, new ArrayFactory[ScFieldId] {
        def create(count: Int): Array[ScFieldId] = new Array[ScFieldId](count)
      }).toSeq: _*)
    } else
      collection.immutable.Sequence(findChildrenByClass(classOf[ScFieldId]).toSeq: _*)
  }

  override def toString: String = "ListOfIdentifiers"
}