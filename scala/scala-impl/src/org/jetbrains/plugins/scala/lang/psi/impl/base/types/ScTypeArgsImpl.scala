package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl

class ScTypeArgsImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypeArgs {
  override def toString: String = "TypeArgumentsList"

  override def typeArguments: Seq[ScTypeArgument] =
    getChildren.toSeq.collect {
      case typeArg: ScTypeArgument => typeArg
    }

  override def deleteChildInternal(child: ASTNode): Unit = {
    val args = this.typeArguments
    val childIsTypeArg = args.exists(_.getNode == child)
    def childIsLastArgToBeDeleted = args.lengthIs == 1 && childIsTypeArg

    if (childIsLastArgToBeDeleted) this.delete()
    else if (childIsTypeArg) ScalaPsiUtil.deleteElementInCommaSeparatedList(this, child)
    else super.deleteChildInternal(child)
  }
}
