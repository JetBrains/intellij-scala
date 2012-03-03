package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import api.statements.{ScFunctionDefinition, ScFunction, ScFunctionDeclaration}
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import com.intellij.util.io.StringRef
import impl.ScFunctionStubImpl
import index.ScalaIndexKeys

/**
 * User: Alexander Podkhalyuzin
 * Date: 14.10.2008
 */

abstract class ScFunctionElementType[Func <: ScFunction](debugName: String)
extends ScStubElementType[ScFunctionStub, ScFunction](debugName) {
  def createStubImpl[ParentPsi <: PsiElement](psi: ScFunction, parentStub: StubElement[ParentPsi]): ScFunctionStub = {
    val returnTypeText = {
      psi.returnTypeElement match {
        case Some(x) => x.getText
        case None => ""
      }
    }
    val bodyText = {
      if (returnTypeText != "") {
        ""
      } else {
        psi match {
          case fDef: ScFunctionDefinition => fDef.body match {
            case Some(x) => x.getText
            case None => ""
          }
          case _ => ""
        }
      }
    }
    val assign = {
      psi match {
        case fDef: ScFunctionDefinition => fDef.hasAssign
        case _ => false
      }
    }
    val isImplicit = psi.hasModifierProperty("implicit")
    val isMacro = psi match {
      case fDef: ScFunctionDefinition => fDef.isMacro
      case _ => false
    }
    new ScFunctionStubImpl[ParentPsi](parentStub, this, psi.name, psi.isInstanceOf[ScFunctionDeclaration],
      psi.annotationNames.toArray, returnTypeText, bodyText, assign, isImplicit, isMacro)
  }

  def serialize(stub: ScFunctionStub, dataStream: StubOutputStream) {
    dataStream.writeName(stub.getName)
    dataStream.writeBoolean(stub.isDeclaration)
    val annotations = stub.getAnnotations
    dataStream.writeByte(annotations.length)
    for (annotation <- annotations) {
      dataStream.writeName(annotation)
    }
    dataStream.writeName(stub.getReturnTypeText)
    dataStream.writeName(stub.getBodyText)
    dataStream.writeBoolean(stub.hasAssign)
    dataStream.writeBoolean(stub.isImplicit)
    dataStream.writeBoolean(stub.isMacro)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScFunctionStub = {
    val name = dataStream.readName
    val isDecl = dataStream.readBoolean
    val length = dataStream.readByte
    val annotations = new Array[StringRef](length)
    for (i <- 0 until length) {
      annotations(i) = dataStream.readName
    }
    val parent = parentStub.asInstanceOf[StubElement[PsiElement]]
    val returnTypeText = dataStream.readName
    val bodyText = dataStream.readName
    val assign = dataStream.readBoolean
    val isImplicit = dataStream.readBoolean()
    val isMacro = dataStream.readBoolean()
    new ScFunctionStubImpl(parent, this, name, isDecl, annotations, returnTypeText, bodyText, assign, isImplicit, isMacro)
  }

  def indexStub(stub: ScFunctionStub, sink: IndexSink) {

    val name = stub.getName
    if (name != null) {
      sink.occurrence(ScalaIndexKeys.METHOD_NAME_KEY, name)
    }
    if (stub.isImplicit) sink.occurrence(ScalaIndexKeys.IMPLICITS_KEY, "implicit")
  }
}