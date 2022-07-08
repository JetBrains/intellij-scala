package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.types._

class ScSequenceArgImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScSequenceArg{
  override def toString: String = "SequenceArgumentType"
}