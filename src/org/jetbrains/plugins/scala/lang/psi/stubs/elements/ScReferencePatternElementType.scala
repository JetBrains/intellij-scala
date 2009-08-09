package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements


import api.base.patterns.ScReferencePattern
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs._
import com.intellij.util.io.StringRef
import impl.{ScReferencePatternStubImpl}
import psi.impl.base.patterns.ScReferencePatternImpl
/**
 * User: Alexander Podkhalyuzin
 * Date: 17.07.2009
 */

class ScReferencePatternElementType[Func <: ScReferencePattern]
extends ScStubElementType[ScReferencePatternStub, ScReferencePattern]("reference pattern") {
  def createStubImpl[ParentPsi <: PsiElement](psi: ScReferencePattern, parentStub: StubElement[ParentPsi]): ScReferencePatternStub = {
    new ScReferencePatternStubImpl[ParentPsi](parentStub, this, psi.getName)
  }

  def serialize(stub: ScReferencePatternStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
  }

  def createPsi(stub: ScReferencePatternStub): ScReferencePattern = {
    new ScReferencePatternImpl(stub)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScReferencePatternStub = {
    val name = StringRef.toString(dataStream.readName)
    new ScReferencePatternStubImpl(parentStub.asInstanceOf[StubElement[_ <: PsiElement]], this, name)
  }

  def indexStub(stub: ScReferencePatternStub, sink: IndexSink): Unit = {}
}