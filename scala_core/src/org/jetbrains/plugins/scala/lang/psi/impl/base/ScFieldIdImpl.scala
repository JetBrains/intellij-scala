package org.jetbrains.plugins.scala.lang.psi.impl.base

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.base._
import _root_.scala.collection.mutable._
import org.jetbrains.plugins.scala.lang.psi.api.statements._

/**
* @author ilyas
*/

class ScFieldIdImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScFieldId {

  override def toString: String = "Field identifier"

  def nameId = findChildByType(ScalaTokenTypes.tIDENTIFIER)

  def isMutable = getParent match {
    case l: ScIdList =>
      l.getParent match {
        case v: ScVariable => true
        case _ => false
      }
    case _ => false
  }
}