package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs._
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.impl.base.patterns.ScReferencePatternImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScReferencePatternStubImpl

/**
  * User: Alexander Podkhalyuzin
  * Date: 17.07.2009
  */
class ScReferencePatternElementType extends ScStubElementType[ScReferencePatternStub, ScReferencePattern]("reference pattern") {
  override def serialize(stub: ScReferencePatternStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScReferencePatternStub =
    new ScReferencePatternStubImpl(parentStub, this,
      nameRef = dataStream.readName)

  override def createStub(pattern: ScReferencePattern, parentStub: StubElement[_ <: PsiElement]): ScReferencePatternStub =
    new ScReferencePatternStubImpl(parentStub, this,
      nameRef = StringRef.fromString(pattern.name))

  override def createElement(node: ASTNode): ScReferencePattern = new ScReferencePatternImpl(node)

  override def createPsi(stub: ScReferencePatternStub): ScReferencePattern = new ScReferencePatternImpl(stub)
}