package org.jetbrains.plugins.scala.lang.psi.impl.toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.extensions.{PsiMemberExt, ifReadAllowed}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGivenAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionDefinitionImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFunctionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScFunctionElementType

class ScGivenAliasDefinitionImpl(
  stub: ScFunctionStub[ScGivenAliasDefinition],
  nodeType: ScFunctionElementType[ScGivenAliasDefinition],
  node: ASTNode
) extends ScFunctionDefinitionImpl(stub, nodeType, node)
  with ScGivenAliasDeclarationOrDefinitionImpl
  with ScGivenAliasDefinition {

  override def acceptScala(visitor: ScalaElementVisitor): Unit =
    visitor.visitGivenAliasDefinition(this)

  override def toString: String = "ScGivenAliasDefinition: " + ifReadAllowed(name)("")

  override protected def keywordTokenType: IElementType = ScalaTokenType.GivenKeyword

  /**
   * @note given definitions are final by default, unless they are deferred
   *       (implying that they will be implemented in subclasses)
   */
  override def isEffectivelyFinal: Boolean = !isDeferred

  override def isDeferred: Boolean = {
    val rhsRef = this.body match {
      case Some(ref: ScReferenceExpression) => ref
      case _ =>
        return false
    }

    // NOTE: this is a micro-optimization to avoid redundant resolve for references that are 100% not deferred
    // This is possible because Scala compiler requires "deferred" to be used under its own name.
    // If you try to do this:
    //   import scala.compiletime.{deferred => myDeferred2}
    //   given Boolean = myDeferred2
    // Scala compiler will generate this error:
    //   `deferred` can only be used as the right hand side of a given definition in a trait.
    //     Note that `deferred` can only be used under its own name when implementing a given in a trait
    //
    // Note, this micro-optimization might seem redundant, but it could be useful in the cases where generally no resolve is expected.
    // For example, in `ScGivenAliasDefinitionImpl.isEffectivelyFinal` it would be nice to avoid redundant resolve.
    if (rhsRef.refName != "deferred")
      return false

    val resolved = rhsRef.bind()
    resolved.exists(srr => isScalaCompileDeferred(srr.element))
  }

  private def isScalaCompileDeferred(element: PsiElement): Boolean =
    element match {
      case f: ScFunctionDefinition =>
        f.qualifiedNameOpt.orNull == "scala.compiletime.deferred"
      case _ =>
        false
    }
}
