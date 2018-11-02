package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.{ASTNode, Language}
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDeclaration, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.statements.{ScFunctionDeclarationImpl, ScFunctionDefinitionImpl, ScMacroDefinitionImpl}
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScFunctionStubImpl

/**
  * User: Alexander Podkhalyuzin
  * Date: 14.10.2008
  */
abstract class ScFunctionElementType(debugName: String,
                                     language: Language = ScalaLanguage.INSTANCE)
  extends ScStubElementType[ScFunctionStub, ScFunction](debugName, language) {

  override def serialize(stub: ScFunctionStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
    dataStream.writeBoolean(stub.isDeclaration)
    dataStream.writeNames(stub.annotations)
    dataStream.writeOptionName(stub.typeText)
    dataStream.writeOptionName(stub.bodyText)
    dataStream.writeBoolean(stub.hasAssign)
    dataStream.writeBoolean(stub.isImplicit)
    dataStream.writeBoolean(stub.isLocal)
  }

  override def deserialize(dataStream: StubInputStream,
                           parentStub: StubElement[_ <: PsiElement]) = new ScFunctionStubImpl(
    parentStub,
    this,
    nameRef = dataStream.readName,
    isDeclaration = dataStream.readBoolean,
    annotationsRefs = dataStream.readNames,
    typeTextRef = dataStream.readOptionName,
    bodyTextRef = dataStream.readOptionName,
    hasAssign = dataStream.readBoolean,
    isImplicit = dataStream.readBoolean,
    isLocal = dataStream.readBoolean
  )

  override def createStubImpl(function: ScFunction, parentStub: StubElement[_ <: PsiElement]): ScFunctionStub = {
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
        maybeDefinition.flatMap(_.body)
          .map(_.getText)
    }

    val annotations = function.annotations
      .map(_.annotationExpr.constr.typeElement)
      .asReferences { text =>
        text.substring(text.lastIndexOf('.') + 1)
      }

    new ScFunctionStubImpl(parentStub, this,
      nameRef = StringRef.fromString(function.name),
      isDeclaration = function.isInstanceOf[ScFunctionDeclaration],
      annotationsRefs = annotations,
      typeTextRef = returnTypeText.asReference,
      bodyTextRef = bodyText.asReference,
      hasAssign = maybeDefinition.exists(_.hasAssign),
      isImplicit = function.hasModifierProperty("implicit"),
      isLocal = function.containingClass == null)
  }

  override def indexStub(stub: ScFunctionStub, sink: IndexSink): Unit = {
    sink.occurrences(index.ScalaIndexKeys.METHOD_NAME_KEY, stub.getName)
    if (stub.isImplicit) sink.implicitOccurence()
  }
}

object FunctionDeclaration extends ScFunctionElementType("function declaration") {

  override def createElement(node: ASTNode) = new ScFunctionDeclarationImpl(null, null, node)

  override def createPsi(stub: ScFunctionStub) = new ScFunctionDeclarationImpl(stub, this, null)
}

object FunctionDefinition extends ScFunctionElementType("function definition") {

  override def createElement(node: ASTNode) = new ScFunctionDefinitionImpl(null, null, node)

  override def createPsi(stub: ScFunctionStub) = new ScFunctionDefinitionImpl(stub, this, null)
}

object MacroDefinition extends ScFunctionElementType("macro definition") {

  override def createElement(node: ASTNode) = new ScMacroDefinitionImpl(null, null, node)

  override def createPsi(stub: ScFunctionStub) = new ScMacroDefinitionImpl(stub, this, null)
}