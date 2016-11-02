package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPatternList
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScPatternListImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScPatternListStubImpl

/**
  * User: Alexander Podkhalyuzin
  * Date: 17.07.2009
  */
class ScPatternListElementType extends ScStubElementType[ScPatternListStub, ScPatternList]("pattern list") {
  override def serialize(stub: ScPatternListStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.simplePatterns)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScPatternListStub =
    new ScPatternListStubImpl(parentStub, this,
      simplePatterns = dataStream.readBoolean)

  override def createStub(patterns: ScPatternList, parentStub: StubElement[_ <: PsiElement]): ScPatternListStub =
    new ScPatternListStubImpl(parentStub, this,
      simplePatterns = patterns.simplePatterns)

  override def createElement(node: ASTNode): ScPatternList = new ScPatternListImpl(node)

  override def createPsi(stub: ScPatternListStub): ScPatternList = new ScPatternListImpl(stub)
}