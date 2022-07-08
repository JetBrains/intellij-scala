package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.types._

class ScTypesImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) with ScTypes {
      override def toString: String = "TypesList"
}