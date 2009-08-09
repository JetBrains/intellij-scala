package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import com.intellij.psi.scope._

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScEnumeratorsImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScEnumerators {

  override def toString: String = "Enumerators"

  def enumerators = findChildrenByClass(classOf[ScEnumerator])

  def generators = findChildrenByClass(classOf[ScGenerator])

  def guards = findChildrenByClass(classOf[ScGuard])

  def namings = for (c <- getChildren if c.isInstanceOf[ScGenerator] || c.isInstanceOf[ScEnumerator])
          yield c.asInstanceOf[{def pattern: ScPattern}]

  type Patterned = {
    def pattern: ScPattern
  }

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    import org.jetbrains.plugins.scala.lang.resolve._

    val ns = namings.reverse
    val begin = if (ns.contains(lastParent)) ns.drop(ns.indexOf(lastParent)) else ns
    val patts = begin.map((ns: Patterned) => ns.pattern).filter(_ != null)    
    val binds = patts.flatMap((p: ScPattern) => p.bindings)
    for (b <- binds) if (!processor.execute(b, state)) return false
    true
  }

}