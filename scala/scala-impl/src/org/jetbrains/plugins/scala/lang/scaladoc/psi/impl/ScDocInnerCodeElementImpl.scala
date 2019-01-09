package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocInnerCodeElement

/**
 * User: Dmitry Naidanov
 * Date: 11/14/11
 */

class ScDocInnerCodeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocInnerCodeElement {
  override def toString = "InnerCodeElement"
}