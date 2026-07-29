package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream, StubSerializingElementFactory}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.packaging.ScPackagingImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPackagingStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScPackagingStubImpl
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

final class ScPackagingElementType extends ScalaStubBasedElementType[ScPackagingStub, ScPackaging](ScPackagingElementType.DebugName) {
  override def createElement(node: ASTNode): ScPackaging = new ScPackagingImpl(node)
}

object ScPackagingElementType {
  val DebugName = "packaging"
}

class ScPackagingStubFactory(elementType: IElementType) extends StubSerializingElementFactory[ScPackagingStub, ScPackaging] {

  override def serialize(stub: ScPackagingStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.packageName)
    dataStream.writeName(stub.parentPackageName)
    dataStream.writeBoolean(stub.isExplicit)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScPackagingStub =
    new ScPackagingStubImpl(
      parentStub,
      elementType,
      packageName = dataStream.readNameString,
      parentPackageName = dataStream.readNameString,
      isExplicit = dataStream.readBoolean
    )

  override def createStub(packaging: ScPackaging, parentStub: StubElement[_ <: PsiElement]): ScPackagingStub =
    ScStubElementType.Processing.run {
      new ScPackagingStubImpl(
        parentStub,
        elementType,
        packageName = packaging.packageName,
        parentPackageName = packaging.parentPackageName,
        isExplicit = packaging.isExplicit
      )
    }

  override def createPsi(stub: ScPackagingStub): ScPackaging = new ScPackagingImpl(stub)

  override def indexStub(stub: ScPackagingStub, sink: IndexSink): Unit = {
    import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.PACKAGE_FQN_KEY

    val prefix = stub.parentPackageName
    var ownNamePart = stub.packageName

    def append(postfix: String): String =
      ScalaNamesUtil.cleanFqn(if (prefix.nonEmpty) prefix + "." + postfix else postfix)

    var i = 0
    do {
      sink.occurrence[ScPackaging, CharSequence](PACKAGE_FQN_KEY, append(ownNamePart))
      i = ownNamePart.lastIndexOf(".")
      if (i > 0) {
        ownNamePart = ownNamePart.substring(0, i)
      }
    } while (i > 0)
  }

  override def getExternalId: String = s"scala.${ScPackagingElementType.DebugName}"

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)
}
