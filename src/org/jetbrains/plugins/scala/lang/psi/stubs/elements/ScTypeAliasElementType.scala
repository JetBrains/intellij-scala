package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.extensions.MaybePsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDeclaration, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTypeAliasStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
  * User: Alexander Podkhalyuzin
  * Date: 18.10.2008
  */
abstract class ScTypeAliasElementType[Func <: ScTypeAlias](debugName: String)
  extends ScStubElementType[ScTypeAliasStub, ScTypeAlias](debugName) {
  override def serialize(stub: ScTypeAliasStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
    dataStream.writeBoolean(stub.isDeclaration)
    dataStream.writeName(stub.getTypeElementText)
    dataStream.writeName(stub.getLowerBoundElementText)
    dataStream.writeName(stub.getUpperBoundElementText)
    dataStream.writeBoolean(stub.isLocal)
    dataStream.writeBoolean(stub.isStableQualifier)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScTypeAliasStub = {
    val name = StringRef.toString(dataStream.readName)
    val isDecl = dataStream.readBoolean
    val parent = parentStub.asInstanceOf[StubElement[PsiElement]]
    val typeElementText = dataStream.readName.toString
    val lower = dataStream.readName.toString
    val upper = dataStream.readName.toString
    val isLocal = dataStream.readBoolean()
    val isStable = dataStream.readBoolean()
    new ScTypeAliasStubImpl(parent, this, name, isDecl, typeElementText, lower, upper, isLocal, isStable)
  }

  override def createStub(psi: ScTypeAlias, parentStub: StubElement[_ <: PsiElement]): ScTypeAliasStub = {
    val isDeclaration = psi.isInstanceOf[ScTypeAliasDeclaration]
    val typeElementText = {
      if (isDeclaration) ""
      else {
        psi.asInstanceOf[ScTypeAliasDefinition].aliasedTypeElement.getText
      }
    }
    val lower = {
      if (!isDeclaration) ""
      else psi.asInstanceOf[ScTypeAliasDeclaration].lowerTypeElement.text
    }
    val upper = {
      if (!isDeclaration) ""
      else psi.asInstanceOf[ScTypeAliasDeclaration].upperTypeElement.text
    }
    val containingClass = psi.containingClass
    val isStableQualifier = ScalaPsiUtil.hasStablePath(psi) && containingClass.isInstanceOf[ScObject]
    new ScTypeAliasStubImpl(parentStub, this, psi.name, isDeclaration, typeElementText, lower, upper,
      containingClass == null, isStableQualifier)
  }

  override def indexStub(stub: ScTypeAliasStub, sink: IndexSink): Unit = {
    val name = ScalaNamesUtil.cleanFqn(stub.getName)
    if (name != null) {
      sink.occurrence(ScalaIndexKeys.TYPE_ALIAS_NAME_KEY, name)
      if (stub.isStableQualifier) {
        sink.occurrence(ScalaIndexKeys.STABLE_ALIAS_NAME_KEY, name)
      }
    }
  }
}