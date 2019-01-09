package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl


import _root_.org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocMethodParams

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.07.2008
 */
 
class ScDocMethodParamsImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocMethodParams{
  override def toString: String = "DocMethodParams"
}