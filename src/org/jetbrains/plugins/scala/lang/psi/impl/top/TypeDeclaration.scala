package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.lang.ASTNode
/**
 * User: Dmitry.Krasilschikov
 * Date: 03.10.2006
 * Time: 19:43:50
 */

  case class ScTmplDefIMpl ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    //todo
    override def toString: String = ""
  }

  abstract class ScTypeDefImpl ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    def getDefStr : String = getParent.getFirstChild.getText
    override def toString : String = getDefStr + " :" + getFirstChild
  }    