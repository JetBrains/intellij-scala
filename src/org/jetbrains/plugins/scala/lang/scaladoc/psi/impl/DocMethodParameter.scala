package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl


import _root_.org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import api.ScDocMethodParameter
import com.intellij.lang.ASTNode

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */
 
class ScDocMethodParameterImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocMethodParameter{
  override def toString: String = "DocMethodParameter"
}