package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

import com.intellij.util.ArrayFactory
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import com.intellij.lang.ASTNode
import stubs.ScParamClausesStub;
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import com.intellij.psi._
/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScParametersImpl extends ScalaStubBasedElementImpl[ScParameters] with ScParameters {

  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScParamClausesStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "Parameters"

  def params: Seq[ScParameter] = clauses.flatMap((clause: ScParameterClause) => clause.parameters)

  def clauses: Seq[ScParameterClause] = {
    getStubOrPsiChildren(ScalaElementTypes.PARAM_CLAUSE, new ArrayFactory[ScParameterClause]{
      def create(count: Int): Array[ScParameterClause] = new Array[ScParameterClause](count)
    }).toSeq
  }

  def getParameterIndex(p: PsiParameter) = params.indexOf(List(p))

  def getParametersCount = params.length

  override def getParameters = params.toArray

  def addClause(clause: ScParameterClause): ScParameters = {
    getNode.addChild(clause.getNode)
    return this
  }
}