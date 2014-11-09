package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs._
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScFieldIdImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScFieldIdStubImpl
/**
 * User: Alexander Podkhalyuzin
 * Date: 19.07.2009
 */

class ScFieldIdElementType[Func <: ScFieldId]
extends ScStubElementType[ScFieldIdStub, ScFieldId]("field id") {
  def createStubImpl[ParentPsi <: PsiElement](psi: ScFieldId, parentStub: StubElement[ParentPsi]): ScFieldIdStub = {
    new ScFieldIdStubImpl[ParentPsi](parentStub, this, psi.name)
  }

  def serialize(stub: ScFieldIdStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
  }

  def createPsi(stub: ScFieldIdStub): ScFieldId = {
    new ScFieldIdImpl(stub)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScFieldIdStub = {
    val name = StringRef.toString(dataStream.readName)
    new ScFieldIdStubImpl(parentStub.asInstanceOf[StubElement[_ <: PsiElement]], this, name)
  }

  def indexStub(stub: ScFieldIdStub, sink: IndexSink): Unit = {}
}