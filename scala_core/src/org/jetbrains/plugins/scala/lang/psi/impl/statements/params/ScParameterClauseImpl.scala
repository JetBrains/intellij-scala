package org.jetbrains.plugins.scala.lang.psi.impl.statements.params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import stubs.elements.wrappers.DummyASTNode
import stubs.ScParamClauseStub;
import com.intellij.psi._
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._


/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScParameterClauseImpl(node: ASTNode) extends ScalaStubBasedElementImpl[ScParameterClause](node) with ScParameterClause {

  def this(stub: ScParamClauseStub) = {
    this(DummyASTNode)
    setStub(stub)
    setNode(null)
  }

  override def toString: String = "ParametersClause"

  def parameters: Seq[ScParameter] = findChildrenByClass(classOf[ScParameter])
}