package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.TokenSets._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl

class ScTypeArgsImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypeArgs {
  override def toString: String = "TypeArgumentsList"

  override def typeArgs: Seq[ScTypeElement] = getChildren.toSeq
    .filter(e => TYPE_ELEMENTS_TOKEN_SET.contains(e.getNode.getElementType))
    .map(_.asInstanceOf[ScTypeElement])

  override def deleteChildInternal(child: ASTNode): Unit = {
    val args = this.typeArgs
    val childIsTypeArg = args.exists(_.getNode == child)
    def childIsLastArgToBeDeleted = args.lengthIs == 1 && childIsTypeArg

    if (childIsLastArgToBeDeleted) this.delete()
    else if (childIsTypeArg) ScalaPsiUtil.deleteElementInCommaSeparatedList(this, child)
    else super.deleteChildInternal(child)
  }
}