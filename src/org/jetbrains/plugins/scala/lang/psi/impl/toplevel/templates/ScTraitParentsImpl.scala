package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package templates

import com.intellij.psi.PsiElement
import types.ScType
import stubs.ScTemplateParentsStub;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._


import com.intellij.psi.tree._

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
    typeElements.map(_.calcType)
  }
}