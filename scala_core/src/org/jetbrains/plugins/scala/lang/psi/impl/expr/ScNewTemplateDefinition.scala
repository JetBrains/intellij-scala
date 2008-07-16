package org.jetbrains.plugins.scala.lang.psi.impl.expr

import types.ScCompoundType
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._
import api.expr._

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScNewTemplateDefinitionImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScNewTemplateDefinition {
  override def toString: String = "NewTemplateDefinition"

  override def getType = {
    val (holders, aliases) = extendsBlock.templateBody match {
      case Some(b) => (b.holders, b.aliases)
      case None => (Seq.empty, Seq.empty)
    }
    new ScCompoundType(extendsBlock.superTypes, holders, aliases)
  }
}