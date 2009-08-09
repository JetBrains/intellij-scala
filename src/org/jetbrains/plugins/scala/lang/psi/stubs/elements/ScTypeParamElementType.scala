package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements


import api.statements.params.ScTypeParam
import com.intellij.util.io.StringRef
import psi.impl.statements.params.ScTypeParamImpl
import com.intellij.psi.stubs.{StubOutputStream, IndexSink, StubElement, StubInputStream}
import com.intellij.psi.PsiElement
import impl.ScTypeParamStubImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

class ScTypeParamElementType[Func <: ScTypeParam]
        extends ScStubElementType[ScTypeParamStub, ScTypeParam]("type parameter") {
  def serialize(stub: ScTypeParamStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
    dataStream.writeName(stub.getUpperText)
    dataStream.writeName(stub.getLowerText)
    dataStream.writeName(stub.getViewText)
  }

  def indexStub(stub: ScTypeParamStub, sink: IndexSink): Unit = {}

  def createPsi(stub: ScTypeParamStub): ScTypeParam = {
    new ScTypeParamImpl(stub)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScTypeParam, parentStub: StubElement[ParentPsi]): ScTypeParamStub = {
    val upperText = psi.upperTypeElement match {
      case Some(te) => te.getText
      case None => ""
    }
    val lowerText = psi.lowerTypeElement match {
      case Some(te) => te.getText
      case None => ""
    }
    val viewText = psi.viewTypeElement match {
      case Some(te) => te.getText
      case None => ""
    }
    new ScTypeParamStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, psi.getName,
      upperText, lowerText, viewText)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScTypeParamStub = {
    val name = StringRef.toString(dataStream.readName)
    val upperText = StringRef.toString(dataStream.readName)
    val lowerText = StringRef.toString(dataStream.readName)
    val viewText = StringRef.toString(dataStream.readName)
    new ScTypeParamStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, name,
      upperText, lowerText, viewText)
  }
}