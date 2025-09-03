package org.jetbrains.plugins.scala.lang.psi.impl.base
package types

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, ifReadAllowed}
import org.jetbrains.plugins.scala.lang.TokenSets
import org.jetbrains.plugins.scala.lang.ir.typeTree.{TypeTree, TypeTreeHolder}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScSelfTypeElementStub
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

import scala.collection.mutable

class ScSelfTypeElementImpl private(stub: ScSelfTypeElementStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementType.SELF_TYPE, node) with ScSelfTypeElement {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScSelfTypeElementStub) = this(stub, null)

  override def toString: String = "SelfType: " + ifReadAllowed(name)("")

  override def nameId: PsiElement = findChildByType[PsiElement](TokenSets.SELF_TYPE_ID)

  override def `type`(): TypeResult = {
    val parent = PsiTreeUtil.getParentOfType(this, classOf[ScTemplateDefinition])
    assert(parent != null)

    typeTreeHolder match {
      case Some(tt) =>
        for {
          templateType <- parent.`type`()
          selfType     <- tt.`type`()
        } yield
          if (this.isInScala3File) ScAndType(templateType, selfType)
          else                     ScCompoundType(Seq(templateType, selfType))
      case None => parent.`type`()
    }
  }

  override def typePsiElement: Option[ScTypeElement] = findChild[ScTypeElement]
  override def typeTreeHolder: Option[TypeTreeHolder] = byPsiOrStub[Option[TypeTreeHolder]](typePsiElement)(_.typeTreeHolder)

  override def classNames: Array[String] = byStubOrPsi(_.classNames) {
    val names = mutable.ArrayBuffer.empty[String]

    def fillNames(typeTree: TypeTree): Unit = {
      typeTree match {
        case TypeTree.SimpleType(name) => names += name
        case TypeTree.ParenthesizedType(inner) => fillNames(inner)
        case TypeTree.CompoundType(components, _) => components.foreach(fillNames)
        case _ => //do nothing
      }
    }

    typeTreeHolder.foreach(tth => fillNames(tth.typeTree))
    names.toArray
  }
}