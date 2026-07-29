package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.parser.SelfPsiCreator

/**
 * Common super-trait for Scala stub-bearing element types, carrying the stub/PSI type parameters so
 * that [[org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl]] subclasses can infer
 * them from the node-type argument passed to the constructor.
 *
 * Implemented by both the legacy [[ScStubElementType]] (an `IStubElementType`) and the migrated
 * [[ScalaStubBasedElementType]] (a plain `IElementType`), so the two coexist during the stub/PSI
 * decoupling (SCL-23400).
 */
trait ScTypedElementType[S <: StubElement[T], T <: PsiElement] extends SelfPsiCreator {
  override def createElement(node: ASTNode): T
}
