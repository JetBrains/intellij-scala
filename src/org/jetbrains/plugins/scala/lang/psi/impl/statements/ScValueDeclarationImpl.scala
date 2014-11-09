package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScValueStub
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypingContext}

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 * Time: 9:55:28
 */

class ScValueDeclarationImpl extends ScalaStubBasedElementImpl[ScValue] with ScValueDeclaration {
  def this(node: ASTNode) = {this (); setNode(node)}

  def this(stub: ScValueStub) = {this (); setStub(stub); setNode(null)}

  override def toString: String = "ScValueDeclaration: " + declaredElements.map(_.name).mkString(", ")

  def declaredElements = getIdList.fieldIds

  override def getType(ctx: TypingContext) = typeElement match {
    case None => Failure(ScalaBundle.message("no.type.element.found", getText), Some(this))
    case Some(te) => te.getType(ctx)
  }

  def typeElement: Option[ScTypeElement] = {
    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScValueStub].getTypeElement
    }
    else findChild(classOf[ScTypeElement])
  }

  def getIdList: ScIdList = {
    val stub = getStub
    if (stub != null) {
      stub.getChildrenByType(ScalaElementTypes.IDENTIFIER_LIST, JavaArrayFactoryUtil.ScIdListFactory).apply(0)
    } else findChildByClass(classOf[ScIdList])
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitValueDeclaration(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitValueDeclaration(this)
      case _ => super.accept(visitor)
    }
  }
}