package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates

import api.statements.ScTypeAlias
import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.util.ArrayFactory
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import stubs.{ScTemplateBodyStub, ScPrimaryConstructorStub}

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:38:04
*/

class ScTemplateBodyImpl extends ScalaStubBasedElementImpl[ScTemplateBody] with ScTemplateBody
                                        with ScImportsHolder {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScTemplateBodyStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "ScTemplateBody"

  def aliases: Array[ScTypeAlias] = {
    val stub = getStub
    if (stub != null) {
      stub.getChildrenByType(TokenSets.ALIASES_SET, new ArrayFactory[ScTypeAlias] {
        def create(count: Int): Array[ScTypeAlias] = new Array[ScTypeAlias](count)
      })
    } else findChildrenByClass(classOf[ScTypeAlias])
  }
}