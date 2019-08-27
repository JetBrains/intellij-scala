package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScAnonymousGivenParameterClause, ScParameter, ScParameterClause, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScParamClauseStub

class ScAnonymousGivenParameterClauseImpl private(stub: ScParamClauseStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementType.ANONYMOUS_GIVEN_PARAM_CLAUSE, node)
    with ScAnonymousGivenParameterClause {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScParamClauseStub) = this(stub, null)

  def parameters: Seq[ScParameter] = Seq.empty

  def anonymousGivenTypeElements: Seq[ScTypeElement] = findChildrenByClassScala(classOf[ScTypeElement])

  override def effectiveParameters: Seq[ScParameter] = {
    getParent
      .asOptionOf[ScParameters]
      .map(parameters => parameters -> parameters.getParent.isInstanceOf[ScPrimaryConstructor])
      .map { case (parameters, isClassParameter) =>
        val params =
          for ((tyElem, idx) <- anonymousGivenTypeElements.zipWithIndex)
            yield "x$" + idx + ": " + tyElem.getText
        ScalaPsiElementFactory
          .createImplicitClauseFromTextWithContext(params, parameters, isClassParameter)
          .parameters
      }
      .getOrElse(Seq.empty)
  }

  override def isImplicit: Boolean = false

  override def isGiven: Boolean = true

  /**
   * add parameter as last parameter in clause
   * if clause has repeated parameter, add before this parameter.
   */
  override def addParameter(param: ScParameter): ScParameterClause = ???

  override def owner: PsiElement = {
    ScalaPsiUtil.getContextOfType(this, true, classOf[ScFunctionExpr], classOf[ScFunction], classOf[ScPrimaryConstructor])
  }
}
