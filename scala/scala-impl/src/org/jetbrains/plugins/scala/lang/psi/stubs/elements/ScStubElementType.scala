package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.{ASTNode, Language}
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs._
import org.jetbrains.plugins.scala.lang.parser.SelfPsiCreator

/**
  * @author ilyas
  */
abstract class ScStubElementType[S <: StubElement[T], T <: PsiElement](debugName: String,
                                                                       language: Language = ScalaLanguage.INSTANCE)
  extends IStubElementType[S, T](debugName, language)
    with SelfPsiCreator {

  override def createElement(node: ASTNode): T

  override final def createStub(psi: T, parentStub: StubElement[_ <: PsiElement]): S = {
    ScStubElementType.Processing {
      createStubImpl(psi, parentStub)
    }
  }

  protected def createStubImpl(psi: T, parentStub: StubElement[_ <: PsiElement]): S

  override final def getExternalId: String = getLanguage.getDisplayName.toLowerCase + "." + debugName

  override def indexStub(stub: S, sink: IndexSink): Unit = {}

  override def serialize(stub: S, dataStream: StubOutputStream): Unit = {}

  override final def isLeftBound = true
}

object ScStubElementType {

  object Processing {

    private[this] val flag = ThreadLocal.withInitial[Long](() => 0)

    def apply[R](action: => R): R =
      try {
        this (()) += 1
        action
      } finally {
        this (()) -= 1
      }

    implicit def asBoolean(self: this.type): Boolean = apply(()) > 0

    private[this] def apply(unit: Unit): Long = flag.get

    private[this] def update(unit: Unit, long: Long): Unit = flag.set(long)
  }

}