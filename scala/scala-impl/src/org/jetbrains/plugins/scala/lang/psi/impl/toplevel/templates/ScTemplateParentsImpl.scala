package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package templates

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateParentsStub
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.macroAnnotations.Cached

import scala.collection.immutable.ArraySeq

/**
  * @author Alexander Podkhalyuzin
  */
final class ScTemplateParentsImpl private(stub: ScTemplateParentsStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementType.TEMPLATE_PARENTS, node) with ScTemplateParents {

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = visitor.visitTemplateParents(this)

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScTemplateParentsStub) = this(stub, null)

  override def toString: String = "TemplateParents"

  override def allTypeElements: Seq[ScTypeElement] = typeElements ++ syntheticTypeElements

  override def typeElements: Seq[ScTypeElement] = byPsiOrStub(
    constructorInvocations.map(_.typeElement) ++ typeElementsWithoutConstructor
  )(
    _.parentTypeElements
  )

  override def superTypes: Seq[ScType] = {
    if (!isValid) return Seq.empty

    val elements = byStubOrPsi(_.parentTypeElements ++ syntheticTypeElements) {
      allTypeElements
    }

    //for reduced stack size
    val builder = ArraySeq.newBuilder[ScType]
    val iterator = elements.iterator
    while (iterator.hasNext) {
      builder += iterator.next().`type`().getOrAny
    }
    builder.result()
  }

  @Cached(BlockModificationTracker(this), this)
  private def syntheticTypeElements: Seq[ScTypeElement] = getContext.getContext match {
    case td: ScTypeDefinition => SyntheticMembersInjector.injectSupers(td)
    case _ => Seq.empty
  }
}