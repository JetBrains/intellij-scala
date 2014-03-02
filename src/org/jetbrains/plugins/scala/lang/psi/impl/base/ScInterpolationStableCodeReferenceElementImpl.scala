package org.jetbrains.plugins.scala
package lang.psi.impl.base

import com.intellij.lang.ASTNode
import com.intellij.psi.ResolveResult
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScFunction}

/**
 * @author kfeodorov 
 * @since 10.03.14.
 * This class represents Stable Code Reference (as in ConstructorPattern) for InterpolationPattern,
 * but reference to unapply() is taken from ScInterpolatedPrefixReference (a ref to "Object q", that is resolved from interpolator q"") instead
 */
class ScInterpolationStableCodeReferenceElementImpl(node: ASTNode) extends ScStableCodeReferenceElementImpl(node) {

  /**
   *
   * @param incomplete
   * @return reference to scala.reflect.api.Quasiquotes.unapply()
   */
  override def multiResolve(incomplete: Boolean): Array[ResolveResult] = {
    getParent.getChildren.filter{_.isInstanceOf[ScFunctionDefinition]}.lastOption match {
      case Some(unapplyFunc) => Array[ResolveResult] (new ScalaResolveResult(unapplyFunc.asInstanceOf[ScFunction]))
      case None => Array[ResolveResult]()
    }
  }
}
