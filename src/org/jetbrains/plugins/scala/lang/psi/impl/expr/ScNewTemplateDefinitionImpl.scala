package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import toplevel.PsiClassFake
import com.intellij.lang.ASTNode
import com.intellij.psi._
import api.expr._
import api.toplevel.templates.ScTemplateBody
import api.statements.{ScTypeAlias, ScDeclaredElementsHolder}
import collection.mutable.ArrayBuffer
import types.result.{Success, TypingContext}
import api.toplevel.typedef.ScTemplateDefinition
import psi.stubs.ScTemplateDefinitionStub
import icons.Icons
import types._

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScNewTemplateDefinitionImpl private () extends ScalaStubBasedElementImpl[ScTemplateDefinition] with ScNewTemplateDefinition with PsiClassFake {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScTemplateDefinitionStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "NewTemplateDefinition"

  override def getIcon(flags: Int) = Icons.CLASS

  protected override def innerType(ctx: TypingContext) = {
    val (holders, aliases) : (Seq[ScDeclaredElementsHolder], Seq[ScTypeAlias]) = extendsBlock.templateBody match {
      case Some(b: ScTemplateBody) => (b.holders.toSeq, b.aliases.toSeq)
      case None => (Seq.empty, Seq.empty)
    }

    val superTypes = extendsBlock.superTypes.filter(_ match {
      case ScDesignatorType(clazz: PsiClass) => clazz.getQualifiedName != "scala.ScalaObject"
      case _ => true
    })
    if (superTypes.length > 1 || !holders.isEmpty || !aliases.isEmpty) {
      new Success(ScCompoundType(superTypes, holders.toList, aliases.toList, ScSubstitutor.empty), Some(this))
    } else superTypes.headOption match {
      case s@Some(t) => Success(t, Some(this))
      case None => Success(AnyRef, Some(this)) //this is new {} case
    }
  }

 override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState,
                                          lastParent: PsiElement, place: PsiElement): Boolean =
  extendsBlock.templateBody match {
    case Some(body) if (PsiTreeUtil.isContextAncestor(body, place, false)) =>
      super[ScNewTemplateDefinition].processDeclarations(processor, state, lastParent, place)
    case _ => true
  }
  def nameId: PsiElement = null
  override def setName(name: String): PsiElement = throw new IncorrectOperationException("cannot set name")
  override def name: String = "<anonymous>"

  override def getSupers: Array[PsiClass] = {
    val direct = extendsBlock.supers.filter(_ match {
      case clazz: PsiClass => clazz.getQualifiedName != "scala.ScalaObject"
      case _ => true
    }).toArray
    val res = new ArrayBuffer[PsiClass]
    res ++= direct
    for (sup <- direct if !res.contains(sup)) res ++= sup.getSupers
    // return strict superclasses
    res.filter(_ != this).toArray
  }

  override def getExtendsListTypes: Array[PsiClassType] = innerExtendsListTypes

  override def getImplementsListTypes: Array[PsiClassType] = innerExtendsListTypes

  def getTypeWithProjections(ctx: TypingContext, thisProjections: Boolean = false) = getType(ctx) //no projections for new template definition

  override def isInheritor(baseClass: PsiClass, deep: Boolean): Boolean =
    super[ScNewTemplateDefinition].isInheritor(baseClass, deep)

  override def findMethodsAndTheirSubstitutorsByName(name: String, checkBases: Boolean) = {
    super[ScNewTemplateDefinition].findMethodsAndTheirSubstitutorsByName(name, checkBases)
  }
}