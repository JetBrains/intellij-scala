package org.jetbrains.plugins.scala.lang.psi.api.toplevel
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.types.ScType

trait ScImplicitBoundsOwnerImpl extends ScImplicitBoundsOwner {
  override def viewBound: Seq[ScType]    = viewTypeElement.flatMap(_.`type`().toOption)
  override def contextBound: Seq[ScType] = contextBoundTypeElement.flatMap(_.`type`().toOption)

  override def viewTypeElement: Seq[ScTypeElement] = {
    for {
      v <- findChildrenByType(ScalaTokenTypes.tVIEW)
      t <- v.nextSiblingOfType[ScTypeElement]
    } yield t
  }

  override def contextBoundTypeElement: Seq[ScTypeElement] = {
    for {
      v <- findChildrenByType(ScalaTokenTypes.tCOLON)
      t <- v.nextSiblingOfType[ScTypeElement]
    } yield t
  }

  override def removeImplicitBounds(): Unit = {
    var node = getNode.getFirstChildNode
    while (
      node != null && !Set(ScalaTokenTypes.tCOLON, ScalaTokenTypes.tVIEW)(node.getElementType)
    ) {
      node = node.getTreeNext
    }
    if (node == null)
      return
    node.getPsi.getPrevSibling match {
      case ws: PsiWhiteSpace => ws.delete()
      case _                 =>
    }
    node.getTreeParent.removeRange(node, null)
  }

}
