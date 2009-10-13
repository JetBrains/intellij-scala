package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import api.statements.params.{ScParameter, ScParameters}
import types.{Nothing, ScFunctionType};
import com.intellij.psi._
import com.intellij.psi.scope._
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScFunctionExprImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScFunctionExpr {

  override def toString: String = "FunctionExpression"

  def parameters = params.params

  def params = findChildByClass(classOf[ScParameters])

  def result = findChild(classOf[ScExpression])

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    import org.jetbrains.plugins.scala.lang.resolve._

    result match {
      case Some(x) if x == lastParent => {
        for (p <- parameters) {
          if (!processor.execute(p, state)) return false
        }
        true
      }
      case _ => true
    }
  }

  protected override def innerType = {
    val paramTypes = (parameters: Seq[ScParameter]).map((_: ScParameter).calcType)
    (for (r <- result) yield ScFunctionType(r.getType, paramTypes.toList)) getOrElse Nothing
  }

}