package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
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


import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
* @author Alexander Podkhalyuzin, ilyas
*/

class ScUnderscoreSectionImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScUnderscoreSection {
  //todo change parser in the appropriate way!
  override def toString: String = "UnderscoreSection"

  //todo implement me according to SLS-6.23
  override def getType(): ScType = expectedType match {
    case Some(t) => t
    case None => psi.types.Nothing
  }
}