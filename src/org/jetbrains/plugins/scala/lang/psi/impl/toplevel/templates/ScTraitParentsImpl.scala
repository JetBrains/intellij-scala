package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package templates

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateParentsStub
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext


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
      return stub.asInstanceOf[ScTemplateParentsStub].getTemplateParentsTypes
    }
    typeElements.map(_.getType(TypingContext.empty).getOrAny)
  }

  def typeElements: Seq[ScTypeElement] = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScTemplateParentsStub].getTemplateParentsTypeElements
    }
    findChildrenByClassScala(classOf[ScTypeElement])
  }
}