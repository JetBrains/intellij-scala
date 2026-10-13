package org.jetbrains.plugins.scala.lang.psi.stubs.factories

import com.intellij.lang.{ASTNode, Language}
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.FileElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubOutputStream, StubSerializingElementFactory}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.parser.ScCodeBlockElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType.ScExpressionElementType
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType
import org.jetbrains.plugins.scala.lang.psi.tree.IScalaElementType
import org.jetbrains.plugins.scala.util.UnloadableThreadLocal

import scala.annotation.tailrec

abstract class ScStubSerializingElementFactory[Stub <: StubElement[?], Psi <: PsiElement](
  elementType: IScalaElementType,
) extends StubSerializingElementFactory[Stub, Psi] {

  protected def language: Language = ScalaLanguage.INSTANCE

  override final def getExternalId: String = s"${language.getDisplayName.toLowerCase}.$elementType"

  override def serialize(stub: Stub, dataStream: StubOutputStream): Unit = {}

  override def indexStub(stub: Stub, sink: IndexSink): Unit = {}

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubSerializingElementFactory.isLocal(node)

  override final def createStub(psi: Psi, parentStub: StubElement[? <: PsiElement]): Stub = ScStubSerializingElementFactory.Processing.run {
    createStubImpl(psi, parentStub)
  }

  protected def createStubImpl(psi: Psi, parentStub: StubElement[? <: PsiElement]): Stub
}

object ScStubSerializingElementFactory {

  @tailrec
  private def isLocal(node: ASTNode): Boolean = node match {
    case _: FileElement | null => false
    case _ =>
      node.getElementType match {
        case _: ScTemplateDefinitionElementType => false
        case _: ScExpressionElementType | _: ScCodeBlockElementType => true
        case _ => isLocal(node.getTreeParent)
      }
  }

  object Processing {
    private val flag = new UnloadableThreadLocal[Long](0)

    def run[R](action: => R): R =
      try {
        flag.update(_ + 1)
        action
      } finally {
        flag.update(_ - 1)
      }

    def isRunning: Boolean = flag.value > 0
  }
}
