package org.jetbrains.plugins.scala.lang.psi.impl.toplevel

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi._
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

abstract class ScCodeBlockImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScCodeBlock
  with ScDeclarationSequenceHolder {

  def exprs = findChildrenByClass(classOf[ScExpression])
}