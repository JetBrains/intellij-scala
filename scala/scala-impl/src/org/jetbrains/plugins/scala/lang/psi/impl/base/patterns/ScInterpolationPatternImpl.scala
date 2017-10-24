package org.jetbrains.plugins.scala
package lang.psi.impl.base.patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._

/**
 * @author kfeodorov
 * @since 01.03.14.
 */
class ScInterpolationPatternImpl(node: ASTNode) extends ScConstructorPatternImpl(node) with ScInterpolationPattern {

  override def toString: String = "InterpolationPattern"

  override def isMultiLineString: Boolean = getText.endsWith("\"\"\"")
}