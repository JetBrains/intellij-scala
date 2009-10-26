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
import collection.mutable.ArrayBuffer
import types.result.{Failure, Success, TypingContext}

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScNewTemplateDefinitionImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScNewTemplateDefinition with PsiClassFake {
  override def toString: String = "NewTemplateDefinition"

  protected override def innerType(ctx: TypingContext) = {
    val (holders, aliases): (Seq[ScDeclaredElementsHolder], Seq[ScTypeAlias]) = extendsBlock.templateBody match {
      case Some(b: ScTemplateBody) => (b.holders.toSeq, b.aliases.toSeq)
      case None => (Seq.empty, Seq.empty)
    }

    val superTypes = extendsBlock.superTypes
    if (superTypes.length > 1 || !holders.isEmpty || !aliases.isEmpty) {
      new Success(ScCompoundType(superTypes, holders.toList, aliases.toList), Some(this))
    } else superTypes.headOption match {
      case s@Some(t) => Success(t, Some(this))
      case None => Failure("Cannot infre type", Some(this))
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

  override def getSupers: Array[PsiClass] = {
    val direct = extendsBlock.supers.toArray
    val res = new ArrayBuffer[PsiClass]
    res ++= direct
    for (sup <- direct if !res.contains(sup)) res ++= sup.getSupers
    // return strict superclasses
    res.filter(_ != this).toArray
  }


}