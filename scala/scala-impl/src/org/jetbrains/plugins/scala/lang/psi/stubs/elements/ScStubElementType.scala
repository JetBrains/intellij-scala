package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.{ASTNode, Language}
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.FileElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType.ScExpressionElementType
import org.jetbrains.plugins.scala.lang.parser.{ScCodeBlockElementType, SelfPsiCreator}
import org.jetbrains.plugins.scala.lang.psi.tree.IScalaElementType
import org.jetbrains.plugins.scala.util.UnloadableThreadLocal

import scala.annotation.tailrec

/**
 * Base for Scala element types whose stub support is registered separately via a
 * [[com.intellij.psi.stubs.StubSerializingElementFactory]] (see
 * [[org.jetbrains.plugins.scala.lang.psi.stubs.ScalaStubRegistryExtension]]), instead of extending
 * [[com.intellij.psi.stubs.IStubElementType]].
 *
 * Being a plain [[IElementType]], such element types can be loaded on the Remote Development frontend.
 */
abstract class ScStubElementType[T <: PsiElement](debugName: String, language: Language = ScalaLanguage.INSTANCE)
  extends IScalaElementType(debugName, language)
    with SelfPsiCreator {
  override def createElement(node: ASTNode): T
}

object ScStubElementType {

  @tailrec
  private[stubs] def isLocal(node: ASTNode): Boolean = node match {
    case _: FileElement | null => false
    case _ =>
      node.getElementType match {
        case _: ScTemplateDefinitionElementType[_] => false
        case _: ScExpressionElementType | _: ScCodeBlockElementType => true
        case _ => isLocal(node.getTreeParent)
      }
  }

  object Processing {
    private[this] val flag = new UnloadableThreadLocal[Long](0)

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
