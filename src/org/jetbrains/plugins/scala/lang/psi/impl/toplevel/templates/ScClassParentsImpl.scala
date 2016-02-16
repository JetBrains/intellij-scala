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
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext


/**
 * @author Alexander Podkhalyuzin
 */

class ScClassParentsImpl private (stub: StubElement[ScTemplateParents], nodeType: IElementType, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, nodeType, node) with ScClassParents {
  def this(node: ASTNode) = {this(null, null, node)}

  def this(stub: ScTemplateParentsStub) = {this(stub, ScalaElementTypes.CLASS_PARENTS, null)}

  override def toString: String = "ClassParents"

  def superTypes: Seq[ScType] = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScTemplateParentsStub].getTemplateParentsTypes ++ syntheticTypeElements.map(_.getType(TypingContext.empty).getOrAny)
    }
    allTypeElements.map(_.getType(TypingContext.empty).getOrAny)
  }

  def typeElements: Seq[ScTypeElement] = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScTemplateParentsStub].getTemplateParentsTypeElements
    }
    (constructor match {
      case Some(x) => Array[ScTypeElement](x.typeElement)
      case None => Array[ScTypeElement]()
    }) ++ findChildrenByClassScala(classOf[ScTypeElement])
  }
}