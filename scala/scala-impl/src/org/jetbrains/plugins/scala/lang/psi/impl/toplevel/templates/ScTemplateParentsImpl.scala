package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package templates

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateParentsStub
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

import scala.collection.mutable

/**
  * @author Alexander Podkhalyuzin
  */
final class ScTemplateParentsImpl private(stub: ScTemplateParentsStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementType.TEMPLATE_PARENTS, node) with ScTemplateParents {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScTemplateParentsStub) = this(stub, null)

  override def toString: String = "TemplateParents"

  override def allTypeElements: Seq[ScTypeElement] = typeElements ++ syntheticTypeElements

  def typeElements: Seq[ScTypeElement] = byPsiOrStub {
    (constructor.map(_.typeElement) ++ typeElementsWithoutConstructor).toSeq
  } {
    _.parentTypeElements
  }

  def superTypes: Seq[ScType] = {
    if (!isValid) return Seq.empty

    val elements = byStubOrPsi(_.parentTypeElements ++ syntheticTypeElements) {
      allTypeElements
    }

    //for reduced stack size
    val result = mutable.ArrayBuffer.empty[ScType]
    val iterator = elements.iterator
    while (iterator.hasNext) {
      result += iterator.next.`type`().getOrAny
    }
    result
  }

  @Cached(ModCount.getBlockModificationCount, this)
  private def syntheticTypeElements: Seq[ScTypeElement] = getContext.getContext match {
    case td: ScTypeDefinition => SyntheticMembersInjector.injectSupers(td)
    case _ => Seq.empty
  }
}