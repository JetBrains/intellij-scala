package org.jetbrains.plugins.scala.lang.psi.impl.top.params {

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.types._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi._

import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._
/**
 * User: Dmitry.Krasilschikov
 * Date: 13.11.2006
 * Time: 15:29:25
 */
 /************* PARAMETER ****************/
  abstract class Param( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "parameter"
  }

  class ScParam( node : ASTNode ) extends Param ( node ) {
    override def toString: String = super.toString

    def paramType () : ScType = {
      val child = getLastChild 
      child match {
        case paramType : ScType => paramType
        case _ => null
      }
    }
  }

  class ScParamType( node : ASTNode ) extends ScalaPsiElementImpl ( node ) with ScType {
    override def toString: String = "parameter " + super.toString
  }

  class ScClassParam( node : ASTNode ) extends Param ( node ) {
    override def toString: String = "class" + " " + super.toString 
  }

  /************* PARAMETER CLAUSES *****************/
  trait ScParamClauses extends ScalaPsiElement {
    def paramClauses : Iterable[ScParamClause] = {
//      if (this.isInstanceOf[ScParamClause]) return Array(this)

      childrenOfType[ScParamClause](TokenSet.create(Array(ScalaElementTypes.PARAM_CLAUSE)))
    }

    def implicitEnd = getChild(ScalaElementTypes.IMPLICIT_END)
  }

  class ScParamClausesImpl( node : ASTNode ) extends ScalaPsiElementImpl ( node ) with ScParamClauses {
    override def toString: String = "parameters clauses"
  }

  /************* PARAMETER CLAUSE *****************/

  trait ScParamClause extends ScalaPsiElement {
    private def isManyParams = (getChild(ScalaElementTypes.PARAMS) != null)
    private def getParamsNode : ScParams = getChild(ScalaElementTypes.PARAMS).asInstanceOf[ScParamsImpl]

    def params : Iterable[ScParam] = {
      if (isManyParams) return getParamsNode.params

      childrenOfType[ScParam](TokenSet.create(Array(ScalaElementTypes.FUN_PARAM)))
    }
  }

  class ScParamClauseImpl( node : ASTNode ) extends ScalaPsiElementImpl ( node ) with ContiniousIndent with ScParamClause {
    override def toString: String = "parameters clause"
  }

  trait ScParams extends ScalaPsiElement {
    def params : Iterable[ScParam] = childrenOfType[ScParam](TokenSet.create(Array(ScalaElementTypes.FUN_PARAM)))
  }

  class ScParamsImpl (node : ASTNode) extends ScalaPsiElementImpl (node) with ScParams {
    override def toString: String = "parameters"
  }

  /************** TYPE PARAMETER  *********************/
  class TypeParam( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "type parameter"
  }

  case class ScTypeParam( node : ASTNode ) extends TypeParam( node ) {
    override def toString: String = super.toString
  }

  case class ScVariantTypeParam( node : ASTNode ) extends TypeParam( node ) {
    override def toString: String = "variant" + " " + super.toString
  }                   

  /************** TYPE PARAMETER CLAUSE *********************/
  class ScTypeParamClause( node : ASTNode ) extends ScalaPsiElementImpl ( node ) with ContiniousIndent{
    override def toString: String = "type parameter clause"
  }
}