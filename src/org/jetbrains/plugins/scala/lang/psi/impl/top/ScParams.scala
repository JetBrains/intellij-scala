package org.jetbrains.plugins.scala.lang.psi.impl.top.params {

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
/**
 * User: Dmitry.Krasilschikov
 * Date: 13.11.2006
 * Time: 15:29:25
 */
 /************* PARAMETER ****************/
  class Param( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "parameter"
  }

  class ScParam( node : ASTNode ) extends Param ( node ) {
    override def toString: String = super.toString
  }

  class ScClassParam( node : ASTNode ) extends Param ( node ) {
    override def toString: String = "class" + " " + super.toString 
  }

  /************* PARAMETER CLAUSES *****************/

  class ScParamClauses( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "parameters clauses"
  }

/*
  case class ScClassParamClauses( node : ASTNode ) extends ParamClauses ( node ) {
    override def toString: String = "class" + " " + super.toString
  }

  case class ScFunParamClauses( node : ASTNode ) extends ParamClauses ( node ) {
    override def toString: String = "function" + " " + super.toString
  }
  */
  /************* PARAMETER CLAUSE *****************/

  class ScParamClause( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "parameters clause"
  }

  /*case class ScClassParamClause( node : ASTNode ) extends ParamClause ( node ) {
    override def toString: String = "class" + " " + super.toString
  }

  case class ScFunParamClause( node : ASTNode ) extends ParamClause ( node ) {
    override def toString: String = "function" + " " + super.toString
  } */

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
  class ScTypeParamClause( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "type parameter clause"
  }
/*

  case class ScTypeParamClause( node : ASTNode ) extends TypeParamClause ( node ) {
    override def toString: String = super.toString
  }

  case class ScFunctionTypeParamClause( node : ASTNode ) extends TypeParamClause ( node ) {
    override def toString: String = "function" + " " + super.toString
  }
  */
}