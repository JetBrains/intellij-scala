package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import api.base.{ScPrimaryConstructor, ScModifierList}
import api.toplevel.ScEarlyDefinitions
import impl.{ScEarlyDefinitionsStubImpl, ScPrimaryConstructorStubImpl}
import psi.impl.toplevel.ScEarlyDefinitionsImpl
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import com.intellij.psi.PsiElement
import psi.impl.base.ScPrimaryConstructorImpl

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