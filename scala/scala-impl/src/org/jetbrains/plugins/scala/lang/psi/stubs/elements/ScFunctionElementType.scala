package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.{ASTNode, Language}
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDeclaration, ScFunctionDefinition, ScMacroDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.statements.{ScFunctionDeclarationImpl, ScFunctionDefinitionImpl, ScMacroDefinitionImpl}
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScFunctionStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.{ImplicitConversionIndex, ImplicitInstanceIndex}

/**
  * User: Alexander Podkhalyuzin
  * Date: 14.10.2008
  */
abstract class ScFunctionElementType[Fun <: ScFunction](debugName: String,
                                                        language: Language = ScalaLanguage.INSTANCE)
  extends ScStubElementType[ScFunctionStub[Fun], Fun](debugName, language) {

  override def serialize(stub: ScFunctionStub[Fun], dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
    dataStream.writeBoolean(stub.isDeclaration)
    dataStream.writeNames(stub.annotations)
    dataStream.writeOptionName(stub.typeText)
    dataStream.writeOptionName(stub.bodyText)
    dataStream.writeBoolean(stub.hasAssign)
    dataStream.writeBoolean(stub.isImplicitConversion)
    dataStream.writeBoolean(stub.isLocal)
    dataStream.writeOptionName(stub.implicitType)
  }

  override def deserialize(dataStream: StubInputStream,
                           parent: StubElement[_ <: PsiElement]) = new ScFunctionStubImpl(
    parent,
    this,
    name = dataStream.readNameString,
    isDeclaration = dataStream.readBoolean,
    annotations = dataStream.readNames,
    typeText = dataStream.readOptionName,
    bodyText = dataStream.readOptionName,
    hasAssign = dataStream.readBoolean,
    isImplicitConversion = dataStream.readBoolean,
    isLocal = dataStream.readBoolean,
    implicitType = dataStream.readOptionName
  )

  override def createStubImpl(function: Fun,
                              parentStub: StubElement[_ <: PsiElement]): ScFunctionStub[Fun] = {

    val returnTypeElement = function.returnTypeElement

    val returnTypeText = returnTypeElement.map(_.getText)

    val maybeDefinition = function.asOptionOf[ScFunctionDefinition]

    val bodyText = returnTypeText match {
      case Some(_) => None
      case None =>
        maybeDefinition.flatMap(_.body)
          .map(_.getText)
    }

    val annotations = function.annotations
      .map(_.annotationExpr.constructorInvocation.typeElement)
      .asStrings { text =>
        text.substring(text.lastIndexOf('.') + 1)
      }

    new ScFunctionStubImpl(parentStub, this,
      name = function.name,
      isDeclaration = function.isInstanceOf[ScFunctionDeclaration],
      annotations = annotations,
      typeText = returnTypeText,
      bodyText = bodyText,
      hasAssign = maybeDefinition.exists(_.hasAssign),
      isImplicitConversion = function.isImplicitConversion,
      isLocal = function.containingClass == null,
      implicitType = ScImplicitInstanceStub.implicitType(function, function.returnTypeElement))
  }

  override def indexStub(stub: ScFunctionStub[Fun], sink: IndexSink): Unit = {
    import index.ScalaIndexKeys._
    sink.occurrences(METHOD_NAME_KEY, stub.getName)

    if (stub.isImplicitConversion)
      ImplicitConversionIndex.occurrence(sink)
    else
      ImplicitInstanceIndex.occurrence(sink, stub.implicitType)
  }
}

object FunctionDeclaration extends ScFunctionElementType[ScFunctionDeclaration]("function declaration") {

  override def createElement(node: ASTNode) = new ScFunctionDeclarationImpl(null, null, node)

  override def createPsi(stub: ScFunctionStub[ScFunctionDeclaration]) = new ScFunctionDeclarationImpl(stub, this, null)
}

object FunctionDefinition extends ScFunctionElementType[ScFunctionDefinition]("function definition") {

  override def createElement(node: ASTNode) = new ScFunctionDefinitionImpl(null, null, node)

  override def createPsi(stub: ScFunctionStub[ScFunctionDefinition]) = new ScFunctionDefinitionImpl(stub, this, null)
}

object MacroDefinition extends ScFunctionElementType[ScMacroDefinition]("macro definition") {

  override def createElement(node: ASTNode) = new ScMacroDefinitionImpl(null, null, node)

  override def createPsi(stub: ScFunctionStub[ScMacroDefinition]) = new ScMacroDefinitionImpl(stub, this, null)
}