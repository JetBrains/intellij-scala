package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil.ScFieldIdFactory
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScFieldId, ScIdList}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScIdListStub

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScIdListImpl private (stub: ScIdListStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, IDENTIFIER_LIST, node) with ScIdList {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScIdListStub) = this(stub, null)

  def fieldIds: Seq[ScFieldId] = getStubOrPsiChildren(FIELD_ID, ScFieldIdFactory)

  override def toString: String = "ListOfIdentifiers"
}