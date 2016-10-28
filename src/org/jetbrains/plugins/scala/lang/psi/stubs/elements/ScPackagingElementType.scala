package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import com.intellij.util.io.StringRef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.packaging.ScPackagingImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScPackagingStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
  * @author ilyas
  */

class ScPackagingElementType extends ScStubElementType[ScPackagingStub, ScPackaging]("packaging") {
  override def serialize(stub: ScPackagingStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.parentPackageName)
    dataStream.writeName(stub.packageName)
    dataStream.writeBoolean(stub.isExplicit)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScPackagingStub =
    new ScPackagingStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this,
      parentPackageNameRef = dataStream.readName,
      packageNameRef = dataStream.readName,
      isExplicit = dataStream.readBoolean)

  override def createStub(packaging: ScPackaging, parentStub: StubElement[_ <: PsiElement]): ScPackagingStub =
    new ScPackagingStubImpl(parentStub, this,
      parentPackageNameRef = fromString(packaging.parentPackageName),
      packageNameRef = fromString(packaging.packageName),
      isExplicit = packaging.isExplicit)

  override def indexStub(stub: ScPackagingStub, sink: IndexSink): Unit = {
    val prefix = stub.parentPackageName
    var ownNamePart = stub.packageName

    def append(postfix: String) =
      ScalaNamesUtil.cleanFqn(if (prefix.length > 0) prefix + "." + postfix else postfix)

    var i = 0
    do {
      sink.occurrence(ScalaIndexKeys.PACKAGE_FQN_KEY, append(ownNamePart).hashCode: java.lang.Integer)
      i = ownNamePart.lastIndexOf(".")
      if (i > 0) {
        ownNamePart = ownNamePart.substring(0, i)
      }
    } while (i > 0)
  }

  override def createElement(node: ASTNode): ScPackaging = new ScPackagingImpl(node)

  override def createPsi(stub: ScPackagingStub): ScPackaging = new ScPackagingImpl(stub)
}
