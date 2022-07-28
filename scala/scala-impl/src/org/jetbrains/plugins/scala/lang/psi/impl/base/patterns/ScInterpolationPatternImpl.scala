package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScInterpolationPattern
import org.jetbrains.plugins.scala.lang.psi.impl.base.literals.QuotedLiteralImplBase

final class ScInterpolationPatternImpl(node: ASTNode) extends ScConstructorPatternImpl(node) with ScInterpolationPattern {

  override def toString: String = "InterpolationPattern"

  override def isMultiLineString: Boolean = getText.endsWith(QuotedLiteralImplBase.MultiLineQuote)
}