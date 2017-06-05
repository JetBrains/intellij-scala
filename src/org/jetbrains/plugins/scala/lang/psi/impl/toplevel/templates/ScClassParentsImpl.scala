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
  */
class ScClassParentsImpl private(stub: ScTemplateParentsStub[ScClassParents], node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementTypes.CLASS_PARENTS, node) with ScClassParents {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScTemplateParentsStub[ScClassParents]) = this(stub, null)

  override def toString: String = "ClassParents"

  def superTypes: Seq[ScType] = {
    val elements = byStubOrPsi(_.parentTypeElements ++ syntheticTypeElements)(allTypeElements)

    //for reduced stacksize
    val iterator = elements.iterator
    val buffer = ArrayBuffer[ScType]()
    while (iterator.hasNext) {
      buffer += iterator.next.getType().getOrAny
    }
    buffer
  }

  def typeElements: Seq[ScTypeElement] = byPsiOrStub {
    constructor.toSeq.map(_.typeElement) ++ findChildrenByClassScala(classOf[ScTypeElement])
  } {
    _.parentTypeElements
  }
}