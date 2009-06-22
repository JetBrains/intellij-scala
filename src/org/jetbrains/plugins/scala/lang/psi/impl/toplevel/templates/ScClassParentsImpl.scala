package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates

import stubs.ScTemplateParentsStub

import types.ScType;
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._


/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:22:09
*/

class ScClassParentsImpl extends ScalaStubBasedElementImpl[ScTemplateParents] with ScClassParents {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScTemplateParentsStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "ClassParents"


  def superTypes: Seq[ScType] = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScTemplateParentsStub].getTemplateParentsTypes.toSeq
    }
    typeElements.map(_.calcType)
  }
}