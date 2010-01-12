package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements


import api.base.types.ScSelfTypeElement
import impl.ScSelfTypeElementStubImpl
import psi.impl.base.types.ScSelfTypeElementImpl
import com.intellij.psi.stubs.{IndexSink, StubInputStream, StubElement, StubOutputStream}

import com.intellij.psi.PsiElement
/**
 * User: Alexander Podkhalyuzin
 * Date: 19.06.2009
 */

class ScSelfTypeElementElementType[Func <: ScSelfTypeElement]
        extends ScStubElementType[ScSelfTypeElementStub, ScSelfTypeElement]("self type element") {
  def serialize(stub: ScSelfTypeElementStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
    dataStream.writeName(stub.getTypeElementText)
  }

  def createPsi(stub: ScSelfTypeElementStub): ScSelfTypeElement = {
    new ScSelfTypeElementImpl(stub)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScSelfTypeElement, parentStub: StubElement[ParentPsi]): ScSelfTypeElementStub = {
    new ScSelfTypeElementStubImpl(parentStub, this, psi.name, psi.typeElement match {case None => "" case Some(x) => x.getText})
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScSelfTypeElementStub = {
    val name = dataStream.readName
    val typeElementText = dataStream.readName
    new ScSelfTypeElementStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, name.toString, typeElementText.toString)
  }

  def indexStub(stub: ScSelfTypeElementStub, sink: IndexSink): Unit = {}
}