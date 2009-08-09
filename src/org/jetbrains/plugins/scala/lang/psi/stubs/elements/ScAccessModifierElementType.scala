package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements


import api.base.{ScAccessModifier, ScModifierList}
import com.intellij.psi.stubs.{StubOutputStream, IndexSink, StubElement, StubInputStream}


import com.intellij.psi.PsiElement
import impl.ScAccessModifierStubImpl
import psi.impl.base.ScAccessModifierImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

class ScAccessModifierElementType[Func <: ScAccessModifier]
        extends ScStubElementType[ScAccessModifierStub, ScAccessModifier]("access modifier") {
  def serialize(stub: ScAccessModifierStub, dataStream: StubOutputStream): Unit = {}

  def indexStub(stub: ScAccessModifierStub, sink: IndexSink): Unit = {}

  def createPsi(stub: ScAccessModifierStub): ScAccessModifier = {
    new ScAccessModifierImpl(stub)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScAccessModifier, parentStub: StubElement[ParentPsi]): ScAccessModifierStub = {
    new ScAccessModifierStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScAccessModifierStub = {
    new ScAccessModifierStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this)
  }
}