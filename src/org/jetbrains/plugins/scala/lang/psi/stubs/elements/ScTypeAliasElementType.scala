package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import com.intellij.util.io.StringRef.fromString
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDeclaration, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTypeAliasStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.{STABLE_ALIAS_NAME_KEY, TYPE_ALIAS_NAME_KEY}

/**
  * User: Alexander Podkhalyuzin
  * Date: 18.10.2008
  */
abstract class ScTypeAliasElementType[Func <: ScTypeAlias](debugName: String)
  extends ScStubElementType[ScTypeAliasStub, ScTypeAlias](debugName) {
  override def serialize(stub: ScTypeAliasStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
    dataStream.writeOptionName(stub.typeElementText)
    dataStream.writeOptionName(stub.lowerBoundElementText)
    dataStream.writeOptionName(stub.upperBoundElementText)
    dataStream.writeBoolean(stub.isLocal)
    dataStream.writeBoolean(stub.isDeclaration)
    dataStream.writeBoolean(stub.isStableQualifier)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScTypeAliasStub = {
    new ScTypeAliasStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this,
      nameRef = dataStream.readName,
      typeElementTextRef = dataStream.readOptionName,
      lowerTypeElementTextRef = dataStream.readOptionName,
      upperTypeElementTextRef = dataStream.readOptionName,
      isLocal = dataStream.readBoolean,
      isDeclaration = dataStream.readBoolean,
      isStableQualifier = dataStream.readBoolean)
  }

  override def createStub(alias: ScTypeAlias, parentStub: StubElement[_ <: PsiElement]): ScTypeAliasStub = {
    val maybeAlias = Option(alias)

    val aliasedTypeText = maybeAlias.collect {
      case definition: ScTypeAliasDefinition => definition
    }.flatMap {
      _.aliasedTypeElement
    }.map {
      _.getText
    }

    val maybeDeclaration = maybeAlias.collect {
      case declaration: ScTypeAliasDeclaration => declaration
    }
    val lowerBoundText = maybeDeclaration.flatMap {
      _.lowerTypeElement
    }.map {
      _.getText
    }
    val upperBoundText = maybeDeclaration.flatMap {
      _.upperTypeElement
    }.map {
      _.getText
    }

    val maybeContainingClass = maybeAlias.map {
      _.containingClass
    }
    val isStableQualifier = maybeContainingClass.collect {
      case o: ScObject if ScalaPsiUtil.hasStablePath(alias) => o
    }.isDefined

    new ScTypeAliasStubImpl(parentStub, this,
      nameRef = fromString(alias.name),
      typeElementTextRef = aliasedTypeText.asReference,
      lowerTypeElementTextRef = lowerBoundText.asReference,
      upperTypeElementTextRef = upperBoundText.asReference,
      isLocal = maybeContainingClass.isEmpty,
      isDeclaration = maybeDeclaration.isDefined,
      isStableQualifier = isStableQualifier)
  }

  override def indexStub(stub: ScTypeAliasStub, sink: IndexSink): Unit = {
    val names = Array(stub.getName)
    this.indexStub(names, sink, TYPE_ALIAS_NAME_KEY)
    if (stub.isStableQualifier) {
      this.indexStub(names, sink, STABLE_ALIAS_NAME_KEY)
    }
  }
}