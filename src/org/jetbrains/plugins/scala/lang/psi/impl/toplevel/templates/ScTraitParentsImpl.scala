package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates

import stubs.ScTemplateParentsStub
import types.ScType;

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
      stub match {
        case tp: ScTemplateParentsStub => {
          return tp.getTemplateParentsTypesTexts.map(ScalaPsiElementFactory.createTypeFromText(_, this)).toSeq
        }
        case _ =>
      }
    }
    typeElements.map(_.calcType)
  }
}