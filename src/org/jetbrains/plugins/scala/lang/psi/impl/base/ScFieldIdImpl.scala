package org.jetbrains.plugins.scala.lang.psi.impl.base

import api.statements.ScVariable
import api.toplevel.ScImportableDeclarationsOwner
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi.api.base._

/**
 * @author ilyas
 */

class ScFieldIdImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScFieldId with ScImportableDeclarationsOwner {
  override def toString: String = "Field identifier"

  def nameId = findChildByType(ScalaTokenTypes.tIDENTIFIER)

  override def isStable = getParent match {
    case l: ScIdList => l.getParent match {
      case _: ScVariable => false
      case _ => true
    }
    case _ => true
  }
}