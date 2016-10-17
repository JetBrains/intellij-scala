package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package templates

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateParentsStub
import org.jetbrains.plugins.scala.lang.psi.types._

/**
  * @author Alexander Podkhalyuzin
  */
class ScClassParentsImpl private(stub: StubElement[ScClassParents], nodeType: IElementType, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, nodeType, node) with ScClassParents {
  def this(node: ASTNode) =
    this(null, null, node)

  def this(stub: ScTemplateParentsStub[ScClassParents]) =
    this(stub, ScalaElementTypes.CLASS_PARENTS, null)

  override def toString: String = "ClassParents"

  def superTypes: Seq[ScType] = {
    val stub = getStub
    val elements = if (stub != null) {
      stub.asInstanceOf[ScTemplateParentsStub[ScClassParents]].parentTypeElements ++ syntheticTypeElements
    } else allTypeElements

    elements.map {
      _.getType().getOrAny
    }
  }

  def typeElements: Seq[ScTypeElement] = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScTemplateParentsStub[ScClassParents]].parentTypeElements
    }
    constructor.toSeq.map {
      _.typeElement
    } ++ findChildrenByClassScala(classOf[ScTypeElement])
  }
}