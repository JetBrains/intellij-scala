package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import psi.stubs.ScSelfTypeElementStub
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import java.lang.String
import psi.types.{ScType, ScCompoundType, ScDesignatorType}
import psi.types.result.{TypeResult, Success, TypingContext}

/**
* @author Alexander Podkhalyuzin
*/

class ScSelfTypeElementImpl extends ScalaStubBasedElementImpl[ScSelfTypeElement] with ScSelfTypeElement{
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScSelfTypeElementStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "SelfType"

  def nameId() = findChildByType(TokenSets.SELF_TYPE_ID)

  def getType(ctx: TypingContext): TypeResult[ScType] = typeElement match {
    case Some(ste) => {
      val self = ste.getType(ctx)
      val parent = PsiTreeUtil.getParentOfType(this, classOf[ScTypeDefinition])
      assert(parent != null)
      Success(ScCompoundType(Seq(ScDesignatorType(parent), self.getOrElse(return self)), Seq.empty, Seq.empty),
        Some(this))
    }
    case None => {
      val parent = PsiTreeUtil.getParentOfType(this, classOf[ScTypeDefinition])
      assert(parent != null)
      Success(ScDesignatorType(parent), Some(this))
    }
  }

}