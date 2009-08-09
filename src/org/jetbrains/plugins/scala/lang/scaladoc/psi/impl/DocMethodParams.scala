package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl


import _root_.org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import api.ScDocMethodParams
import com.intellij.lang.ASTNode

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */
 
class ScDocMethodParamsImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocMethodParams{
  override def toString: String = "DocMethodParams"
}