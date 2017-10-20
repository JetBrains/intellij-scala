package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.stubs.ScSelfTypeElementStub
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

import scala.collection.mutable.ArrayBuffer

/**
  * @author Alexander Podkhalyuzin
  */
class ScSelfTypeElementImpl private(stub: ScSelfTypeElementStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementTypes.SELF_TYPE, node) with ScSelfTypeElement {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScSelfTypeElementStub) = this(stub, null)

  override def toString: String = "SelfType: " + ifReadAllowed(name)("")

  def nameId: PsiElement = findChildByType[PsiElement](TokenSets.SELF_TYPE_ID)

  def `type`(): TypeResult[ScType] = {
    val parent = PsiTreeUtil.getParentOfType(this, classOf[ScTemplateDefinition])
    assert(parent != null)
    typeElement match {
      case Some(ste) =>
        for {
          templateType <- parent.`type`()
          selfType <- ste.`type`()
          ct = ScCompoundType(Seq(templateType, selfType), Map.empty, Map.empty)
        } yield ct
      case None => parent.`type`()
    }
  }

  def typeElement: Option[ScTypeElement] = byPsiOrStub(findChild(classOf[ScTypeElement]))(_.typeElement)

  def classNames: Array[String] = byStubOrPsi(_.classNames) {
    val names = new ArrayBuffer[String]()

    def fillNames(typeElement: ScTypeElement) {
      typeElement match {
        case s: ScSimpleTypeElement => s.reference match {
          case Some(ref) => names += ref.refName
          case _ =>
        }
        case p: ScParameterizedTypeElement => fillNames(p.typeElement)
        case c: ScCompoundTypeElement =>
          c.components.foreach(fillNames)
        case _ => //do nothing
      }
    }

    typeElement.foreach(fillNames)
    names.toArray
  }
}