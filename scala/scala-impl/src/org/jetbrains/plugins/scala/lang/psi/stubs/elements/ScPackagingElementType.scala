package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.{IndexSink, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.packaging.ScPackagingImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.{RawStubElement, ScPackagingStub}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

object ScPackagingElementType extends ScStubElementType[ScPackagingStub, ScPackaging]("packaging") {

  import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScPackagingStubImpl

  override def serialize(stub: ScPackagingStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.packageName)
    dataStream.writeName(stub.parentPackageName)
    dataStream.writeBoolean(stub.isExplicit)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: RawStubElement) =
    new ScPackagingStubImpl(
      parentStub,
      this,
      packageName = dataStream.readNameString,
      parentPackageName = dataStream.readNameString,
      isExplicit = dataStream.readBoolean
    )

  override def createStubImpl(packaging: ScPackaging, parentStub: RawStubElement) =
    new ScPackagingStubImpl(
      parentStub,
      this,
      packageName = packaging.packageName,
      parentPackageName = packaging.parentPackageName,
      isExplicit = packaging.isExplicit
    )

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

  override def createElement(node: ASTNode) = new ScPackagingImpl(null, null, node)

  override def createPsi(stub: ScPackagingStub) = new ScPackagingImpl(stub, this, null)
}
