package org.jetbrains.plugins.scala.lang.psi.impl.top. templateStatemants {;

/**
 * User: Dmitry.Krasilschikov
 * Date: 07.11.2006
 * Time: 15:28:13
 */

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode

  case class ScTemplateStatement ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "Template statement"
  }
    
}