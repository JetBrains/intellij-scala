package org.jetbrains.plugins.scala.lang.psi.impl.base

import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.ir.typeTree.TypeTreeHolder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScContextBound, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeBoundsOwner
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScType, api}
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}

trait ScTypeBoundsOwnerImpl extends ScTypeBoundsOwner {

  override def lowerBound(implicit context: Context): TypeResult = typeOf(lowerTypeTreeHolder, isLower = true)

  override def upperBound(implicit context: Context): TypeResult = typeOf(upperTypeTreeHolder, isLower = false)

  protected def extractBound(in: ScType, isLower: Boolean): ScType = in

  override def viewBound: Seq[ScType] = viewTypeTreeHolders.flatMap(_.`type`().toOption)

  override def contextBound: Seq[ScType] = contextBounds.flatMap(_.typeTreeHolder.`type`().toOption)

  override def upperTypeTreeHolder: Option[TypeTreeHolder] =
    findLastChildByTypeScala[PsiElement](ScalaTokenTypes.tUPPER_BOUND)
      .flatMap(_.nextSiblingOfType[ScTypeElement])

  override def lowerTypeTreeHolder: Option[TypeTreeHolder] =
    findLastChildByTypeScala[PsiElement](ScalaTokenTypes.tLOWER_BOUND)
      .flatMap(_.nextSiblingOfType[ScTypeElement])


  override def viewTypeTreeHolders: Seq[TypeTreeHolder] = {
    for {
      v <- findChildrenByType(ScalaTokenTypes.tVIEW)
      t <- v.nextSiblingOfType[ScTypeElement]
    } yield t
  }

  override def contextBounds: Seq[ScContextBound] = findChildren[ScContextBound]

  override def removeImplicitBounds(): Unit = {
    var node = getNode.getFirstChildNode
    while (node != null && !Set(ScalaTokenTypes.tCOLON, ScalaTokenTypes.tVIEW)(node.getElementType)) {
      node = node.getTreeNext
    }
    if (node == null) return
    node.getPsi.getPrevSibling match {
      case ws: PsiWhiteSpace => ws.delete()
      case _ =>
    }
    node.getTreeParent.removeRange(node, null)
  }

  private def typeOf(typeElement: Option[TypeTreeHolder], isLower: Boolean): TypeResult =
    typeElement match {
      case Some(elem) =>
        if (ScalaApplicationSettings.PRECISE_TEXT) elem.`type`() // SCL-21151
        else elem.`type`().map(extractBound(_, isLower))
      case None => Right(if (isLower) api.Nothing else api.Any)
    }
}