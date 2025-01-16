package org.jetbrains.plugins.scala.lang.psi.impl.base
package types

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.TokenSets.TYPE_WILDCARD_SET
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement.NameId
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.ScExistentialArgument
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

class ScWildcardTypeElementImpl(node: ASTNode)
  extends ScalaPsiElementImpl(node)
  with ScTypeBoundsOwnerImpl
  with ScWildcardTypeElement {
  override protected def innerType: TypeResult =
    for {
      lb <- lowerBound
      ub <- upperBound
    } yield ScExistentialArgument("_$1", Nil, lb, ub)

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitWildcardTypeElement(this)
  }

  override def nameId: NameId.Placeholder = new NameId.Placeholder(findChildByType[PsiElement](TYPE_WILDCARD_SET))
}
