package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.lang.ASTNode
import psi.stubs.{ScVariableStub}
import api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import psi.types.result.TypingContext

/**
 * @author Alexander Podkhalyuzin
 */

class ScVariableDeclarationImpl extends ScalaStubBasedElementImpl[ScVariable] with ScVariableDeclaration {
  def this(node: ASTNode) = {this (); setNode(node)}

  def this(stub: ScVariableStub) = {this (); setStub(stub); setNode(null)}

  override def toString: String = "ScVariableDeclaration"

  def getType(ctx: TypingContext) = wrap(typeElement) flatMap {_.cachedType}

  def declaredElements = getIdList.fieldIds

  def typeElement: Option[ScTypeElement] = {
    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScVariableStub].getTypeElement
    }
    else findChild(classOf[ScTypeElement])
  }

  def getIdList: ScIdList = {
    /*val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScVariableStub].getIdsContainer match {
        case Some(x) => x
        case None => null
      }
    } else */ findChildByClass(classOf[ScIdList])
  }
}