package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements


import api.base.ScPatternList
import impl.{ScPatternListStubImpl}
import psi.impl.base.ScPatternListImpl
import com.intellij.psi.stubs._
import com.intellij.psi.PsiElement
/**
 * User: Alexander Podkhalyuzin
 * Date: 17.07.2009
 */

class ScPatternListElementType[Func <: ScPatternList]
        extends ScStubElementType[ScPatternListStub, ScPatternList]("pattern list") {
  def serialize(stub: ScPatternListStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.allPatternsSimple)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScPatternList, parentStub: StubElement[ParentPsi]): ScPatternListStub = {
    new ScPatternListStubImpl(parentStub, this, psi.allPatternsSimple)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScPatternListStub = {
    val patternsSimple = dataStream.readBoolean
    new ScPatternListStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, patternsSimple)
  }

  def indexStub(stub: ScPatternListStub, sink: IndexSink): Unit = {}

  def createPsi(stub: ScPatternListStub): ScPatternList = {
    new ScPatternListImpl(stub)
  }
}