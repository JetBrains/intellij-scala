package org.jetbrains.plugins.scala.lang.psi.stubs.elements


import api.base.ScIdList
import api.toplevel.ScEarlyDefinitions
import com.intellij.psi.stubs._
import impl.{ScIdListStubImpl, ScEarlyDefinitionsStubImpl}
import psi.impl.base.ScIdListImpl
import psi.impl.toplevel.ScEarlyDefinitionsImpl
import com.intellij.psi.PsiElement
/**
 * User: Alexander Podkhalyuzin
 * Date: 19.07.2009
 */

class ScIdListElementType[Func <: ScIdList]
        extends ScStubElementType[ScIdListStub, ScIdList]("id list") {
  def serialize(stub: ScIdListStub, dataStream: StubOutputStream): Unit = {
  }

  def createPsi(stub: ScIdListStub): ScIdList = {
    new ScIdListImpl(stub)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScIdList, parentStub: StubElement[ParentPsi]): ScIdListStub = {
    new ScIdListStubImpl(parentStub, this)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScIdListStub = {
    new ScIdListStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this)
  }

  def indexStub(stub: ScIdListStub, sink: IndexSink): Unit = {}
}