package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.{ASTNode, Language}
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import com.intellij.util.ArrayUtil.EMPTY_STRING_ARRAY
import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDeclaration, ScFunctionDefinition, ScMacroDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGivenAlias
import org.jetbrains.plugins.scala.lang.psi.impl.statements.{ScFunctionDeclarationImpl, ScFunctionDefinitionImpl, ScMacroDefinitionImpl}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScGivenAliasImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScFunctionStubImpl

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
    dataStream.writeOptionName(stub.implicitConversionParameterClass)
    dataStream.writeBoolean(stub.isLocal)
    dataStream.writeNames(stub.implicitClassNames)
    dataStream.writeBoolean(stub.isTopLevel)
    dataStream.writeOptionName(stub.topLevelQualifier)
    dataStream.writeBoolean(stub.isExtensionMethod)
    dataStream.writeBoolean(stub.isGiven)
    dataStream.writeNames(stub.givenClassNames)
  }

  override def deserialize(dataStream: StubInputStream, parent: StubElement[_ <: PsiElement]) =
    new ScFunctionStubImpl(
      parent,
      this,
      name                             = dataStream.readNameString,
      isDeclaration                    = dataStream.readBoolean,
      annotations                      = dataStream.readNames,
      typeText                         = dataStream.readOptionName,
      bodyText                         = dataStream.readOptionName,
      hasAssign                        = dataStream.readBoolean,
      implicitConversionParameterClass = dataStream.readOptionName,
      isLocal                          = dataStream.readBoolean,
      implicitClassNames               = dataStream.readNames,
      isTopLevel                       = dataStream.readBoolean,
      topLevelQualifier                = dataStream.readOptionName,
      isExtensionMethod                = dataStream.readBoolean,
      isGiven                          = dataStream.readBoolean,
      givenClassNames                  = dataStream.readNames,
    )

  override def createStubImpl(function: Fun,
                              parentStub: StubElement[_ <: PsiElement]): ScFunctionStub[Fun] = {
    val returnTypeElement = function.returnTypeElement

    val returnTypeText = returnTypeElement.map(_.getText)

    val maybeDefinition = function.asOptionOfUnsafe[ScFunctionDefinition]

    val bodyText = returnTypeText match {
      case Some(_) => None
      case None =>
        val text = maybeDefinition.flatMap(_.body).map {
          case block: ScBlockExpr if !block.hasLBrace => s"{${block.getText}}"
          case body                                   => body.getText
        }
        // just for some unpredictable cases when body is empty, e.g. `def this() = ???` is parsed to empty constructor body, see SCL-18521)
        // empty body can lead to issues during building psi element from stubs
        text.filter(StringUtils.isNotEmpty)
    }

    val annotations = function.annotations
      .map(_.annotationExpr.constructorInvocation.typeElement)
      .asStrings { text =>
      text.substring(text.lastIndexOf('.') + 1)
    }

    val implicitConversionParamClass =
      if (function.isImplicitConversion) ScImplicitStub.conversionParamClass(function)
      else None

    val (isGivenAlias, givenAliasClassNames) = function match {
      case alias: ScGivenAlias => (true, ScGivenStub.givenAliasClassNames(alias))
      case _                   => (false, EMPTY_STRING_ARRAY)
    }

    new ScFunctionStubImpl(
      parentStub,
      this,
      name                             = function.name,
      isDeclaration                    = function.isInstanceOf[ScFunctionDeclaration],
      annotations                      = annotations,
      typeText                         = returnTypeText,
      bodyText                         = bodyText,
      hasAssign                        = maybeDefinition.exists(_.hasAssign),
      implicitConversionParameterClass = implicitConversionParamClass,
      isLocal                          = function.containingClass == null,
      implicitClassNames               = ScImplicitStub.implicitClassNames(function, function.returnTypeElement),
      isTopLevel                       = function.isTopLevel,
      topLevelQualifier                = function.topLevelQualifier,
      isExtensionMethod                = function.isExtensionMethod,
      isGiven                          = isGivenAlias,
      givenClassNames                  = givenAliasClassNames,
    )
  }

  override def indexStub(stub: ScFunctionStub[Fun], sink: IndexSink): Unit = {
    import index.ScalaIndexKeys._

    val functionName = stub.getName
    sink.occurrences(METHOD_NAME_KEY, functionName)

    if (stub.isTopLevel) {
      val packageFqn = stub.topLevelQualifier
      packageFqn.foreach(sink.fqnOccurence(TOP_LEVEL_FUNCTION_BY_PKG_KEY, _))
    }

    if (stub.annotations.contains("main")) {
      val packageFqn = stub.topLevelQualifier
      val syntheticClassName = packageFqn.filter(_.nonEmpty).fold("")(_ + ".") + functionName
      sink.occurrences(ANNOTATED_MAIN_FUNCTION_BY_PKG_KEY, syntheticClassName)
    }

    stub.indexImplicits(sink)
    stub.indexGivens(sink)
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

object GivenAlias extends ScFunctionElementType[ScGivenAlias]("given alias") {
  override def createElement(node: ASTNode): ScGivenAlias = new ScGivenAliasImpl(null, null, node)

  override def createPsi(stub: ScFunctionStub[ScGivenAlias]): ScGivenAlias = new ScGivenAliasImpl(stub, this, null)
}