package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import api.statements.{ScTypeAliasDefinition, ScTypeAlias, ScTypeAliasDeclaration}
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import com.intellij.util.io.StringRef
import impl.ScTypeAliasStubImpl
import index.ScalaIndexKeys

/**
 * User: Alexander Podkhalyuzin
 * Date: 18.10.2008
 */

abstract class ScTypeAliasElementType[Func <: ScTypeAlias](debugName: String)
extends ScStubElementType[ScTypeAliasStub, ScTypeAlias](debugName) {
  def createStubImpl[ParentPsi <: PsiElement](psi: ScTypeAlias, parentStub: StubElement[ParentPsi]): ScTypeAliasStub = {
    val isDeclaration = psi.isInstanceOf[ScTypeAliasDeclaration]
    val typeElementText = {
      if (isDeclaration) ""
      else {
        psi.asInstanceOf[ScTypeAliasDefinition].aliasedTypeElement.getText
      }
    }
    val lower = {
      if (!isDeclaration) ""
      else psi.asInstanceOf[ScTypeAliasDeclaration].lowerTypeElement.map(_.getText).getOrElse("")
    }
    val upper = {
      if (!isDeclaration) ""
      else psi.asInstanceOf[ScTypeAliasDeclaration].upperTypeElement.map(_.getText).getOrElse("")
    }
    new ScTypeAliasStubImpl[ParentPsi](parentStub, this, psi.getName, isDeclaration, typeElementText, lower, upper)
  }

  def serialize(stub: ScTypeAliasStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
    dataStream.writeBoolean(stub.isDeclaration)
    dataStream.writeName(stub.getTypeElementText)
    dataStream.writeName(stub.getLowerBoundElementText)
    dataStream.writeName(stub.getUpperBoundElementText)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScTypeAliasStub = {
    val name = StringRef.toString(dataStream.readName)
    val isDecl = dataStream.readBoolean
    val parent = parentStub.asInstanceOf[StubElement[PsiElement]]
    val typeElementText = dataStream.readName.toString
    val lower = dataStream.readName.toString
    val upper = dataStream.readName.toString
    new ScTypeAliasStubImpl(parent, this, name, isDecl, typeElementText, lower, upper)
  }

  def indexStub(stub: ScTypeAliasStub, sink: IndexSink): Unit = {
    val name = stub.getName
    if (name != null) {
      sink.occurrence(ScalaIndexKeys.TYPE_ALIAS_NAME_KEY, name)
    }
  }
}