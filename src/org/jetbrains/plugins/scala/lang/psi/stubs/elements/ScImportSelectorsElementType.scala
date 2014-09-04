package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements


import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
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

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScImportSelectorsStub = {
    val hasWildcard = dataStream.readBoolean
    new ScImportSelectorsStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, hasWildcard)
  }

  def indexStub(stub: ScImportSelectorsStub, sink: IndexSink): Unit = {}

  def createPsi(stub: ScImportSelectorsStub): ScImportSelectors = {
    new ScImportSelectorsImpl(stub)
  }
}