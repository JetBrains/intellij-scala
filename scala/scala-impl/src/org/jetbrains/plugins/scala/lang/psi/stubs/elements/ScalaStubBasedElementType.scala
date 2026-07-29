package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.{ASTNode, Language}
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.ScalaLanguage

/**
 * Base for Scala element types whose stub support is registered separately via a
 * [[com.intellij.psi.stubs.StubSerializingElementFactory]] (see
 * [[org.jetbrains.plugins.scala.lang.psi.stubs.ScalaStubRegistryExtension]]), instead of extending
 * [[com.intellij.psi.stubs.IStubElementType]].
 *
 * Being a plain [[IElementType]], such element types can be loaded on the Remote Development frontend.
 */
abstract class ScalaStubBasedElementType[S <: StubElement[T], T <: PsiElement](
  debugName: String,
  language: Language = ScalaLanguage.INSTANCE
) extends IElementType(debugName, language)
  with ScTypedElementType[S, T] {

  override def createElement(node: ASTNode): T

  override final def isLeftBound: Boolean = true
}
