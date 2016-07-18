package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelectors
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports.ScImportSelectorsImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScImportSelectorsStubImpl

/**
  * User: Alexander Podkhalyuzin
  * Date: 20.06.2009
  */

class ScImportSelectorsElementType[Func <: ScImportSelectors]
  extends ScStubElementType[ScImportSelectorsStub, ScImportSelectors]("import selectors") {
  def serialize(stub: ScImportSelectorsStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.hasWildcard)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScImportSelectors, parentStub: StubElement[ParentPsi]): ScImportSelectorsStub = {
    new ScImportSelectorsStubImpl(parentStub, this, psi.hasWildcard)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScImportSelectorsStub = {
    val hasWildcard = dataStream.readBoolean
    new ScImportSelectorsStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, hasWildcard)
  }

  override def createPsi(stub: ScImportSelectorsStub): ScImportSelectors = new ScImportSelectorsImpl(stub)

  override def createElement(node: ASTNode): ScImportSelectors = new ScImportSelectorsImpl(node)
}