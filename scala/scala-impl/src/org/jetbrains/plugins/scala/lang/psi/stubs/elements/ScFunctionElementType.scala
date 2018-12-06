package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.{ASTNode, Language}
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDeclaration, ScFunctionDefinition, ScMacroDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.statements.{ScFunctionDeclarationImpl, ScFunctionDefinitionImpl, ScMacroDefinitionImpl}
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScFunctionStubImpl

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
    dataStream.writeBoolean(stub.isImplicit)
    dataStream.writeBoolean(stub.isLocal)
  }

  override def deserialize(dataStream: StubInputStream,
                           parent: StubElement[_ <: PsiElement]) = new ScFunctionStubImpl(
    parent,
    this,
    name = dataStream.readNameString,
    isDeclaration = dataStream.readBoolean,
    annotations = dataStream.readNames,
    typeText = dataStream.readOptionName,
    isImplicit = dataStream.readBoolean,
    isLocal = dataStream.readBoolean
  )

  override def createStubImpl(function: Fun,
                              parentStub: StubElement[_ <: PsiElement]): ScFunctionStub[Fun] = {

    val funDef = function match {
      case definition: ScFunctionDefinition => definition
      case _ => null
    }

    val returnTypeText = function match {
      case definition: ScFunctionDefinition if !definition.hasAssign => Some("_root_.scala.Unit")
      case _ => function.returnTypeElement.map(_.getText)
    }

    val annotations = function.annotations
      .map(_.annotationExpr.constr.typeElement)
      .asStrings { text =>
        text.substring(text.lastIndexOf('.') + 1)
      }

    new ScFunctionStubImpl(parentStub, this,
      name = function.name,
      isDeclaration = function.isInstanceOf[ScFunctionDeclaration],
      annotations = annotations,
      typeText = returnTypeText,
      isImplicit = function.hasModifierProperty("implicit"),
      isLocal = function.containingClass == null)
  }

  override def indexStub(stub: ScFunctionStub[Fun], sink: IndexSink): Unit = {
    import index.ScalaIndexKeys._
    sink.occurrences(METHOD_NAME_KEY, stub.getName)
    IMPLICITS_KEY.occurence(sink)
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