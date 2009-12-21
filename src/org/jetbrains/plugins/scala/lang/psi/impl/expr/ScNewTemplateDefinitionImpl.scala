package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import toplevel.PsiClassFake
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._
import api.expr._
import api.toplevel.templates.ScTemplateBody
import api.statements.{ScTypeAlias, ScDeclaredElementsHolder}
import collection.mutable.ArrayBuffer
import types.result.{Failure, Success, TypingContext}
import com.intellij.openapi.project.DumbService
import types.{ScType, ScCompoundType}
import api.toplevel.typedef.{ScTemplateDefinition, ScClass, ScTrait, ScTypeDefinition}
import psi.stubs.ScTemplateDefinitionStub

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScNewTemplateDefinitionImpl private () extends ScalaStubBasedElementImpl[ScTemplateDefinition] with ScNewTemplateDefinition with PsiClassFake {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScTemplateDefinitionStub) = {this(); setStub(stub); setNode(null)}

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
      case None => Failure("Cannot infer type", Some(this))
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

  //todo: it's copy for ScTypeDefinitionImpl
  override def isInheritor(baseClass: PsiClass, deep: Boolean): Boolean = {
    def isInheritorInner(base: PsiClass, drv: PsiClass, deep: Boolean, visited: Set[PsiClass]): Boolean = {
      implicit def option2boolean[T](x: Option[T]): Boolean = x match {case Some(_) => true case _ => false}
      if (visited.contains(drv)) false
      else drv match {
        case drg: ScTemplateDefinition => drg.superTypes.find {
          t => ScType.extractClassType(t) match {
            case Some((c, _)) => {
              val value = baseClass match { //todo: it was wrong to write baseClass.isInstanceOf[c.type]
                case _: ScTrait if c.isInstanceOf[ScTrait] => true
                case _: ScClass if c.isInstanceOf[ScClass] => true
                case _ if !c.isInstanceOf[ScTypeDefinition] => true
                case _ => false
              }
              (c.getQualifiedName == baseClass.getQualifiedName && value) || (deep && isInheritorInner(base, c, deep, visited + drg))
            }
            case _ => false
          }
        }
        case _ => drv.getSuperTypes.find{
          psiT =>
                  val c = psiT.resolveGenerics.getElement
                  if (c == null) false else c == baseClass || (deep && isInheritorInner(base, c, deep, visited + drv))
        }
      }
    }
    if (DumbService.getInstance(baseClass.getProject).isDumb) return false //to prevent failing during indecies
    isInheritorInner(baseClass, this, deep, Set.empty)
  }
}