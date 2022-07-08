package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope._
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor

import scala.collection.immutable.ArraySeq

class ScEnumeratorsImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScEnumerators {

  override def toString: String = "Enumerators"

  override def forBindings: ArraySeq[ScForBinding] = ArraySeq.unsafeWrapArray(findChildrenByClass[ScForBinding](classOf[ScForBinding]))

  override def generators: ArraySeq[ScGenerator] = ArraySeq.unsafeWrapArray(findChildrenByClass[ScGenerator](classOf[ScGenerator]))

  override def guards: ArraySeq[ScGuard] = ArraySeq.unsafeWrapArray(findChildrenByClass[ScGuard](classOf[ScGuard]))

  override def namings: Seq[ScPatterned] =
    for (c <- getChildren.toSeq if c.is[ScGenerator, ScForBinding])
          yield c.asInstanceOf[ScPatterned]

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    val reverseChildren = getChildren.reverse
    val children =
      if (reverseChildren.contains(lastParent)) reverseChildren.drop(reverseChildren.indexOf(lastParent) + (
         lastParent match {
           case _: ScGenerator => 1
           case _ => 0
         }
        ))
      else reverseChildren
    for (c <- children) {
      c match {
        case c: ScGenerator =>
          for (p <- Option(c.pattern); b <- p.bindings) if (!processor.execute(b, state)) return false
          processor match {
            case b: BaseProcessor => b.changedLevel
            case _ =>
          }
        case c: ScForBinding =>
          for (p <- Option(c.pattern); b <- p.bindings) if (!processor.execute(b, state)) return false
        case _ =>
      }
    }
    true
  }

  override def patterns: Seq[ScPattern] = namings.reverse.flatMap(_.pattern.toOption)
}
