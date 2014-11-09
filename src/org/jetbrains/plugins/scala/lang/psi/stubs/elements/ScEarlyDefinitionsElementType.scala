package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.ScEarlyDefinitionsImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScEarlyDefinitionsStubImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

class ScEarlyDefinitionsElementType[Func <: ScEarlyDefinitions]
        extends ScStubElementType[ScEarlyDefinitionsStub, ScEarlyDefinitions]("early definitions") {
  def serialize(stub: ScEarlyDefinitionsStub, dataStream: StubOutputStream): Unit = {
  }

  def createPsi(stub: ScEarlyDefinitionsStub): ScEarlyDefinitions = {
    new ScEarlyDefinitionsImpl(stub)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScEarlyDefinitions, parentStub: StubElement[ParentPsi]): ScEarlyDefinitionsStub = {
    new ScEarlyDefinitionsStubImpl(parentStub, this)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScEarlyDefinitionsStub = {
    new ScEarlyDefinitionsStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this)
  }

  def indexStub(stub: ScEarlyDefinitionsStub, sink: IndexSink): Unit = {}
}