package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import psi.stubs.ScSelfTypeElementStub
import psi.types.result.{Success, TypingContext}

import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScDesignatorType
import java.lang.String

/** 
* @author Alexander Podkhalyuzin
*/

class ScSelfTypeElementImpl extends ScalaStubBasedElementImpl[ScSelfTypeElement] with ScSelfTypeElement{
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScSelfTypeElementStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "SelfType"

  def nameId() = findChildByType(TokenSets.SELF_TYPE_ID)

  def getType(ctx: TypingContext) = typeElement match {
    case Some(ste) => ste.getType(ctx)
    case None => {
      val parent = PsiTreeUtil.getParentOfType(this, classOf[ScTypeDefinition])
      assert(parent != null)
      Success(ScDesignatorType(parent), Some(this))
    }
  }

}