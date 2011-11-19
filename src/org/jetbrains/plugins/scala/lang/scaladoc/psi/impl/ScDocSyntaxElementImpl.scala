package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package impl

import lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.{ScDocSyntaxElement, ScDocTag}

/**
 * User: Dmitry Naidanov
 * Date: 11/14/11
 */

class ScDocSyntaxElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScDocSyntaxElement{
  override def toString = "DocSyntaxElement " + getFlags
}