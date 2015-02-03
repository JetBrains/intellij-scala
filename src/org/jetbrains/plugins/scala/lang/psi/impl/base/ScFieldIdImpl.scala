package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypedDeclaration, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScImportableDeclarationsOwner
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFieldIdStub
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

/**
 * @author ilyas
 */

class ScFieldIdImpl private () extends ScalaStubBasedElementImpl[ScFieldId] with ScFieldId with ScImportableDeclarationsOwner {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScFieldIdStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "Field identifier: " + name

  def getType(ctx: TypingContext) = getParent/*id list*/.getParent match {
    case typed : ScTypedDeclaration => typed.getType(ctx)
    //partial matching
  }

  def nameId: PsiElement = findChildByType[PsiElement](ScalaTokenTypes.tIDENTIFIER)

  override def isStable = getContext match {
    case l: ScIdList => l.getContext match {
      case _: ScVariable => false
      case _ => true
    }
    case _ => true
  }

  override def delete() {
    getContext match {
      case id: ScIdList if id.fieldIds == Seq(this) =>
        id.getContext.delete()
      case _ => throw new UnsupportedOperationException("Cannot delete on id in a list of field ides.")
    }
  }

  override def isVar: Boolean = nameContext.isInstanceOf[ScVariable]

  override def isVal: Boolean = nameContext.isInstanceOf[ScValue]
}