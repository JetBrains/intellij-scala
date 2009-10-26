package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package templates

import types.ScType
import stubs.ScTemplateParentsStub
import types.result.TypingContext
import types._
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._


/**
 * @author Alexander Podkhalyuzin
 */

class ScClassParentsImpl extends ScalaStubBasedElementImpl[ScTemplateParents] with ScClassParents {
  def this(node: ASTNode) = {this (); setNode(node)}

  def this(stub: ScTemplateParentsStub) = {this (); setStub(stub); setNode(null)}

  override def toString: String = "ClassParents"

  def superTypes: Seq[ScType] = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScTemplateParentsStub].getTemplateParentsTypes.toSeq
    }
    typeElements.map(_.getType(TypingContext.empty).getOrElse(Any))
  }
}