package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDeclaration, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScFunctionStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.METHOD_NAME_KEY

/**
  * User: Alexander Podkhalyuzin
  * Date: 14.10.2008
  */
abstract class ScFunctionElementType(debugName: String) extends ScStubElementType[ScFunctionStub, ScFunction](debugName) {

  override def serialize(stub: ScFunctionStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
    dataStream.writeBoolean(stub.isDeclaration)
    dataStream.writeNames(stub.annotations)
    dataStream.writeOptionName(stub.returnTypeText)
    dataStream.writeOptionName(stub.bodyText)
    dataStream.writeBoolean(stub.hasAssign)
    dataStream.writeBoolean(stub.isImplicit)
    dataStream.writeBoolean(stub.isLocal)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScFunctionStub =
    new ScFunctionStubImpl(parentStub, this,
      nameRef = dataStream.readName,
      isDeclaration = dataStream.readBoolean,
      annotationsRefs = dataStream.readNames,
      returnTypeTextRef = dataStream.readOptionName,
      bodyTextRef = dataStream.readOptionName,
      hasAssign = dataStream.readBoolean,
      isImplicit = dataStream.readBoolean,
      isLocal = dataStream.readBoolean)

  override def createStub(function: ScFunction, parentStub: StubElement[_ <: PsiElement]): ScFunctionStub = {
    val maybeFunction = Option(function)
    val returnTypeText = maybeFunction.flatMap {
      _.returnTypeElement
    }.map {
      _.getText
    }

    val maybeDefinition = maybeFunction.collect {
      case definition: ScFunctionDefinition => definition
    }

    val bodyText = returnTypeText match {
      case Some(_) => None
      case None =>
        maybeDefinition.flatMap {
          _.body
        }.map {
          _.getText
        }
    }

    val hasAssign = maybeDefinition.exists {
      _.hasAssign
    }

    val annotations = function.annotations.map {
      _.annotationExpr.constr.typeElement.getText
    }.map { text =>
      text.substring(text.lastIndexOf('.') + 1)
    }.toArray

    new ScFunctionStubImpl(parentStub, this,
      nameRef = StringRef.fromString(function.name),
      isDeclaration = function.isInstanceOf[ScFunctionDeclaration],
      annotationsRefs = annotations.asReferences,
      returnTypeTextRef = returnTypeText.asReference,
      bodyTextRef = bodyText.asReference,
      hasAssign = hasAssign,
      isImplicit = function.hasModifierProperty("implicit"),
      isLocal = function.containingClass == null)
  }

  override def indexStub(stub: ScFunctionStub, sink: IndexSink): Unit = {
    this.indexStub(Array(stub.getName), sink, METHOD_NAME_KEY)
    if (stub.isImplicit) {
      this.indexImplicit(sink)
    }
  }
}