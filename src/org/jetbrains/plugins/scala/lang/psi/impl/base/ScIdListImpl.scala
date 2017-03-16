package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScFieldId, ScIdList}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScIdListStub

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScIdListImpl private (stub: ScIdListStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementTypes.IDENTIFIER_LIST, node) with ScIdList {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScIdListStub) = this(stub, null)

  def fieldIds: Seq[ScFieldId]  = {
    val stub = getStub
    if (stub != null) {
      stub.getChildrenByType(ScalaElementTypes.FIELD_ID, JavaArrayFactoryUtil.ScFieldIdFactory).toSeq
    } else findChildrenByClass(classOf[ScFieldId]).toSeq
  }

  override def toString: String = "ListOfIdentifiers"
}