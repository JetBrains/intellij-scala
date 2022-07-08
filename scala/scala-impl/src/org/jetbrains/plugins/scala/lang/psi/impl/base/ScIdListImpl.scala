package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil.ScFieldIdFactory
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScFieldId, ScIdList}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScIdListStub

class ScIdListImpl private (stub: ScIdListStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, IDENTIFIER_LIST, node) with ScIdList {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScIdListStub) = this(stub, null)

  override def fieldIds: Seq[ScFieldId] = getStubOrPsiChildren(FIELD_ID, ScFieldIdFactory).toSeq

  override def toString: String = "ListOfIdentifiers"
}