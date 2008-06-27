package org.jetbrains.plugins.scala.lang.psi.impl.base

import org.jetbrains.plugins.scala.lang._
import lexer.ScalaTokenTypes
import parser.ScalaElementTypes
import psi.ScalaPsiElementImpl
import psi.api.base._
import psi.types._
import psi.api.toplevel.typedef.ScTypeDefinition
import psi.api.base.types.ScSimpleTypeElement
import psi.impl.ScalaPsiElementFactory
import resolve._
import com.intellij.psi.impl.source.resolve.ResolveCache
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import com.intellij.psi._
import com.intellij.psi.impl._
import org.jetbrains.plugins.scala.icons.Icons
import com.intellij.psi.PsiElement
import com.intellij.openapi.util._
import com.intellij.util.IncorrectOperationException
import api.toplevel.ScTyped
import api.statements.ScTypeAlias

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScStableCodeReferenceElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScStableCodeReferenceElement {

  def bindToElement(element: PsiElement): PsiElement = {
    return this;
    //todo
  }
  def getVariants(): Array[Object] = _resolve(this, new CompletionProcessor(resolveKinds(true))).map(r => r.getElement) //todo
  
  override def toString: String = "CodeReferenceElement"

  object MyResolver extends ResolveCache.PolyVariantResolver[ScStableCodeReferenceElementImpl] {
    def resolve(ref: ScStableCodeReferenceElementImpl, incomplete: Boolean) = {
      _resolve(ref, new ResolveProcessor(ref.resolveKinds(false), refName))
    }
  }

  private def resolveKinds(incomplete : Boolean) = getParent match {
    case _: ScStableCodeReferenceElement => StdKinds.stableQualRef
    case e : ScImportExpr => if (e.selectorSet != None
            //import Class._ is not allowed
            || qualifier == None) StdKinds.stableQualRef else StdKinds.stableQualOrClass 
    case _: ScSimpleTypeElement => if (incomplete) StdKinds.stableQualOrClass else StdKinds.stableClass
    case _ : ScTypeAlias => StdKinds.stableClass
    case _: ScImportSelector => StdKinds.stableImportSelector
    case _ => StdKinds.stableQualRef
  }

  private def _qualifier() : Option[ScStableCodeReferenceElement] = {
    if (getParent.isInstanceOf[ScImportSelector]) {
      return getParent.getParent/*ScImportSelectors*/.getParent.asInstanceOf[ScImportExpr].reference
    }
    qualifier
  }

  def _resolve(ref: ScStableCodeReferenceElementImpl, processor: BaseProcessor): Array[ResolveResult] = {
    _qualifier match {
      case None => {
        def treeWalkUp(place: PsiElement, lastParent: PsiElement): Unit = {
          place match {
            case null => ()
            case p => {
              if (!p.processDeclarations(processor,
              ResolveState.initial,
              lastParent, ref)) return ()
              treeWalkUp(place.getParent, place)
            }
          }
        }
        treeWalkUp(ref, null)
      }
      case Some(q) => {
        q.bind match {
          case None =>
          case Some(ScalaResolveResult(typed : ScTyped, s)) => processType(s.subst(typed.calcType), processor)
          case Some(ScalaResolveResult(pack : PsiPackage, _)) if pack.getQualifiedName == "scala" => {
            import toplevel.synthetic.SyntheticClasses

            for (synth <- SyntheticClasses.get(getProject).getAll) {
              processor.execute(synth, ResolveState.initial)
            }
            pack.processDeclarations(processor, ResolveState.initial, null, ScStableCodeReferenceElementImpl.this)
          }
          case Some(other) => {
            other.element.processDeclarations(processor, ResolveState.initial.put(ScSubstitutor.key, other.substitutor),
            null, ScStableCodeReferenceElementImpl.this)
          }
        }
      }
    }
    processor.candidates.toArray
  }

  def multiResolve(incomplete: Boolean) = {
    getManager.asInstanceOf[PsiManagerEx].getResolveCache.resolveWithCaching(this, MyResolver, false, incomplete)
  }

  def nameId: PsiElement = findChildByType(ScalaTokenTypes.tIDENTIFIER)
}