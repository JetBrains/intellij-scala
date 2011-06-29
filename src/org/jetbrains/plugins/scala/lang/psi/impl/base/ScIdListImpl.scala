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
      stub.getChildrenByType(ScalaElementTypes.FIELD_ID, JavaArrayFactoryUtil.ScFieldIdFactory).toSeq
    } else findChildrenByClass(classOf[ScFieldId]).toSeq
  }

  override def toString: String = "ListOfIdentifiers"
}