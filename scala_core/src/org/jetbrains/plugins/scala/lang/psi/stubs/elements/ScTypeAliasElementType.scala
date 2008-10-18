package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.statements.ScTypeAliasDeclarationImpl
import _root_.org.jetbrains.plugins.scala.lang.psi.impl.statements.ScTypeAliasDefinitionImpl
import api.statements.{ScTypeAlias, ScTypeAliasDeclaration}
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
    new ScTypeAliasStubImpl[ParentPsi](parentStub, this, psi.getName, psi.isInstanceOf[ScTypeAliasDeclaration])
  }

  def serialize(stub: ScTypeAliasStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
    dataStream.writeBoolean(stub.isDeclaration)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScTypeAliasStub = {
    val name = StringRef.toString(dataStream.readName)
    val isDecl = dataStream.readBoolean
    val parent = parentStub.asInstanceOf[StubElement[PsiElement]]
    new ScTypeAliasStubImpl(parent, this, name, isDecl)
  }

  def indexStub(stub: ScTypeAliasStub, sink: IndexSink): Unit = {
    val name = stub.getName
    if (name != null) {
      sink.occurrence(ScalaIndexKeys.TYPE_ALIAS_NAME_KEY, name)
    }
  }
}