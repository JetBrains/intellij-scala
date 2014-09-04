package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScPrimaryConstructorImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScPrimaryConstructorStubImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

class ScPrimaryConstructorElementType[Func <: ScPrimaryConstructor]
        extends ScStubElementType[ScPrimaryConstructorStub, ScPrimaryConstructor]("primary constructor") {
  def serialize(stub: ScPrimaryConstructorStub, dataStream: StubOutputStream): Unit = {
  }

  def createPsi(stub: ScPrimaryConstructorStub): ScPrimaryConstructor = {
    new ScPrimaryConstructorImpl(stub)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScPrimaryConstructor, parentStub: StubElement[ParentPsi]): ScPrimaryConstructorStub = {
    new ScPrimaryConstructorStubImpl(parentStub, this)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScPrimaryConstructorStub = {
    new ScPrimaryConstructorStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this)
  }

  def indexStub(stub: ScPrimaryConstructorStub, sink: IndexSink): Unit = {}
}