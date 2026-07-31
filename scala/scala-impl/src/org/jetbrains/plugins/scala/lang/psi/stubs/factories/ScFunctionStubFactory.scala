package org.jetbrains.plugins.scala.lang.psi.stubs.factories

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import com.intellij.util.ArrayUtil.EMPTY_STRING_ARRAY
import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, ObjectExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDeclaration, ScFunctionDefinition, ScMacroDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScGivenAlias, ScGivenAliasDeclaration, ScGivenAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.statements.{ScFunctionDeclarationImpl, ScFunctionDefinitionImpl, ScMacroDefinitionImpl}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.{ScGivenAliasDeclarationImpl, ScGivenAliasDefinitionImpl}
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.{FunctionDeclaration, FunctionDefinition, GivenAliasDeclaration, GivenAliasDefinition, MacroDefinition, ScFunctionElementType}
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScFunctionStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.{ScFunctionStub, ScGivenStub, ScImplicitStub, ScPackagingStub}

abstract class ScFunctionStubFactory[Fun <: ScFunction](elementType: ScFunctionElementType[Fun])
  extends ScStubSerializingElementFactory[ScFunctionStub[Fun], Fun](elementType) {

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

  override def deserialize(dataStream: StubInputStream, parent: StubElement[_ <: PsiElement]): ScFunctionStub[Fun] =
    new ScFunctionStubImpl(
      parent,
      elementType,
      name = dataStream.readNameString,
      isDeclaration = dataStream.readBoolean,
      annotations = dataStream.readNames,
      typeText = dataStream.readOptionName,
      bodyText = dataStream.readOptionName,
      hasAssign = dataStream.readBoolean,
      implicitConversionParameterClass = dataStream.readOptionName,
      isLocal = dataStream.readBoolean,
      implicitClassNames = dataStream.readNames,
      isTopLevel = dataStream.readBoolean,
      topLevelQualifier = dataStream.readOptionName,
      isExtensionMethod = dataStream.readBoolean,
      isGiven = dataStream.readBoolean,
      givenClassNames = dataStream.readNames,
    )

  override def createStubImpl(function: Fun, parentStub: StubElement[_ <: PsiElement]): ScFunctionStub[Fun] = {
    val returnTypeElement = function.returnTypeElement

    val returnTypeText = returnTypeElement.map(_.getText)

    val maybeDefinition = function.asOptionOfUnsafe[ScFunctionDefinition]

    val bodyText = returnTypeText match {
      case Some(_) => None
      case None =>
        val text = maybeDefinition.flatMap(_.body).map {
          case block: ScBlockExpr if !block.hasLBrace => s"{${block.getText}}"
          case body => body.getText
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
      case _ => (false, EMPTY_STRING_ARRAY)
    }

    new ScFunctionStubImpl(
      parentStub,
      elementType,
      name = function.name,
      isDeclaration = function.isInstanceOf[ScFunctionDeclaration],
      annotations = annotations,
      typeText = returnTypeText,
      bodyText = bodyText,
      hasAssign = maybeDefinition.exists(_.hasAssign),
      implicitConversionParameterClass = implicitConversionParamClass,
      isLocal = function.containingClass == null,
      implicitClassNames = ScImplicitStub.implicitClassNames(function, function.returnTypeElement),
      isTopLevel = function.isTopLevel,
      topLevelQualifier = function.topLevelQualifier,
      isExtensionMethod = function.isExtensionMethod,
      isGiven = isGivenAlias,
      givenClassNames = givenAliasClassNames,
    )
  }

  override def indexStub(stub: ScFunctionStub[Fun], sink: IndexSink): Unit = {
    import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys._

    val functionName = stub.getName
    sink.occurrences(METHOD_NAME_KEY, functionName)

    if (stub.isTopLevel) {
      val packageFqn = stub.topLevelQualifier
      packageFqn.foreach(sink.fqnOccurence(TOP_LEVEL_FUNCTION_BY_PKG_KEY, _))
    }

    if (stub.annotations.contains("main")) {
      val packageFqn = stub.topLevelQualifier.orElse {
        //Handle case when @main method is not toplevel but is inside some object
        //In this case, we should use containing package name, ignoring the containing object names
        val containingPackaging = Iterator.iterate[StubElement[_]](stub)(_.getParentStub)
          .takeWhile(_ != null)
          .findByType[ScPackagingStub]
        containingPackaging.map(_.packageName)
      }
      val syntheticClassName = packageFqn.filter(_.nonEmpty).fold("")(_ + ".") + functionName
      sink.occurrences(ANNOTATED_MAIN_FUNCTION_BY_PKG_KEY, syntheticClassName)
    }

    stub.indexImplicits(sink)
    stub.indexGivens(sink)
  }
}

final class ScFunctionDeclarationStubFactory(elementType: FunctionDeclaration)
  extends ScFunctionStubFactory[ScFunctionDeclaration](elementType) {

  override def createPsi(stub: ScFunctionStub[ScFunctionDeclaration]): ScFunctionDeclaration =
    new ScFunctionDeclarationImpl(stub, elementType, null)
}

final class ScFunctionDefinitionStubFactory(elementType: FunctionDefinition)
  extends ScFunctionStubFactory[ScFunctionDefinition](elementType) {
  override def createPsi(stub: ScFunctionStub[ScFunctionDefinition]): ScFunctionDefinition =
    new ScFunctionDefinitionImpl(stub, elementType, null)
}

final class ScMacroDefinitionStubFactory(elementType: MacroDefinition)
  extends ScFunctionStubFactory[ScMacroDefinition](elementType) {
  override def createPsi(stub: ScFunctionStub[ScMacroDefinition]): ScMacroDefinition =
    new ScMacroDefinitionImpl(stub, elementType, null)
}

final class ScGivenAliasDeclarationStubFactory(elementType: GivenAliasDeclaration)
  extends ScFunctionStubFactory[ScGivenAliasDeclaration](elementType) {
  override def createPsi(stub: ScFunctionStub[ScGivenAliasDeclaration]): ScGivenAliasDeclaration =
    new ScGivenAliasDeclarationImpl(stub, elementType, null)
}

final class ScGivenAliasDefinitionStubFactory(elementType: GivenAliasDefinition)
  extends ScFunctionStubFactory[ScGivenAliasDefinition](elementType) {
  override def createPsi(stub: ScFunctionStub[ScGivenAliasDefinition]): ScGivenAliasDefinition =
    new ScGivenAliasDefinitionImpl(stub, elementType, null)
}
