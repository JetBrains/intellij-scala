package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package templates

import types._
import result.TypingContext
import stubs.ScTemplateParentsStub
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._


/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:22:37
*/

class ScTraitParentsImpl extends ScalaStubBasedElementImpl[ScTemplateParents] with ScTraitParents {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScTemplateParentsStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "TraitParents"

  def superTypes: Seq[ScType] = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScTemplateParentsStub].getTemplateParentsTypes.toSeq
    }
    typeElements.map(_.getType(TypingContext.empty).getOrElse(Any))
  }
}