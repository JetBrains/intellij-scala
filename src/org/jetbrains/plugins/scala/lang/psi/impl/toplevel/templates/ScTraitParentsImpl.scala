package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package templates

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateParentsStub
import org.jetbrains.plugins.scala.lang.psi.types._

import scala.collection.mutable.ArrayBuffer

/**
  * @author Alexander Podkhalyuzin
  *         Date: 22.02.2008
  *         Time: 9:22:37
  */
class ScTraitParentsImpl private(stub: ScTemplateParentsStub[ScTraitParents], node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementTypes.TRAIT_PARENTS, node) with ScTraitParents {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScTemplateParentsStub[ScTraitParents]) = this(stub, null)

  override def toString: String = "TraitParents"

  def superTypes: Seq[ScType] = {
    val elements = byStubOrPsi(_.parentTypeElements ++ syntheticTypeElements)(allTypeElements)

    val buffer = ArrayBuffer[ScType]()
    val iterator = elements.iterator //for reducing stacktrace
    while (iterator.hasNext) {
      buffer += iterator.next().getType().getOrAny
    }
    buffer
  }

  def typeElements: Seq[ScTypeElement] =
    byPsiOrStub(findChildrenByClassScala(classOf[ScTypeElement]).toSeq)(_.parentTypeElements)
}