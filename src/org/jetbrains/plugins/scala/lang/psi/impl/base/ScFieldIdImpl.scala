package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import api.toplevel.ScImportableDeclarationsOwner
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base._
import stubs.{ScFieldIdStub}
import psi.types.result.{Success, TypingContext}
import api.statements.{ScTypedDeclaration, ScVariable}

/**
 * @author ilyas
 */

class ScFieldIdImpl private () extends ScalaStubBasedElementImpl[ScFieldId] with ScFieldId with ScImportableDeclarationsOwner {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScFieldIdStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "Field identifier"

  def getType(ctx: TypingContext) = getParent/*id list*/.getParent match {
    case typed : ScTypedDeclaration => typed.getType(ctx)
    //partial matching
  }

  def nameId = findChildByType(ScalaTokenTypes.tIDENTIFIER)

  override def isStable = getParent match {
    case l: ScIdList => l.getParent match {
      case _: ScVariable => false
      case _ => true
    }
    case _ => true
  }
}