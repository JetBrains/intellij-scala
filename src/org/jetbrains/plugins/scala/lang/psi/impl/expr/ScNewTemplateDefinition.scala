package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import toplevel.PsiClassFake
import types.ScCompoundType
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._
import api.expr._
import api.toplevel.templates.ScTemplateBody
import api.statements.{ScTypeAlias, ScDeclaredElementsHolder}

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScNewTemplateDefinitionImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScNewTemplateDefinition with PsiClassFake {
  override def toString: String = "NewTemplateDefinition"

  override def getType = {
    val (holders, aliases): (Seq[ScDeclaredElementsHolder], Seq[ScTypeAlias]) = extendsBlock.templateBody match {
      case Some(b: ScTemplateBody) => (b.holders.toSeq, b.aliases.toSeq)
      case None => (Seq.empty, Seq.empty)
    }

    val superTypes = extendsBlock.superTypes
    if (superTypes.length > 1 || !holders.isEmpty || !aliases.isEmpty) {
      new ScCompoundType(superTypes, holders.toList, aliases.toList)
    } else superTypes.headOption match {
      case Some(t) => t
      case None => org.jetbrains.plugins.scala.lang.psi.types.Any
    }
  }

 override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState,
                                          lastParent: PsiElement, place: PsiElement): Boolean =
  extendsBlock.templateBody match {
    case Some(body) if (PsiTreeUtil.isContextAncestor(body, place, false)) =>
      super[ScNewTemplateDefinition].processDeclarations(processor, state, lastParent, place)
    case _ => true
  }
  def nameId(): PsiElement = null
  override def setName(name: String): PsiElement = throw new IncorrectOperationException("cannot set name")
  override def name(): String = "<anonymous>"
}