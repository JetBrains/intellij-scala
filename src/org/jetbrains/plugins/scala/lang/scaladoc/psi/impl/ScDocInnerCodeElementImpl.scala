package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl

import com.intellij.lang.ASTNode
import lang.psi.ScalaPsiElementImpl
import api.{ScDocInnerCodeElement, ScDocTag}

/**
 * User: Dmitry Naidanov
 * Date: 11/14/11
 */

class ScDocInnerCodeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocInnerCodeElement {
  override def toString = "InnerCodeElement"
}