package org.jetbrains.plugins.scala.lang.psi.impl.base

import api.ScalaFile
import api.toplevel.packaging.ScPackaging
import api.toplevel.typedef.{ScClass, ScTypeDefinition, ScTrait, ScObject}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang._
import lexer.ScalaTokenTypes
import parser.ScalaElementTypes
import org.jetbrains.annotations._
import psi.ScalaPsiElementImpl
import psi.api.base._
import psi.types._
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
import api.base.patterns.ScConstructorPattern
import api.expr.{ScSuperReference, ScThisReference}
import toplevel.typedef.TypeDefinitionMembers

/**
 * @author AlexanderPodkhalyuzin
 * Date: 22.02.2008
 */

class ScStableCodeReferenceElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScStableCodeReferenceElement {
  def getVariants(): Array[Object] = _resolve(this, new CompletionProcessor(getKinds(true))).map(r => r.getElement)

  override def toString: String = "CodeReferenceElement"

  object MyResolver extends ResolveCache.PolyVariantResolver[ScStableCodeReferenceElementImpl] {
    def resolve(ref: ScStableCodeReferenceElementImpl, incomplete: Boolean) = {
      val kinds = ref.getKinds(false)
      val proc = ref.getParent match {
      //last ref may import many elements with the same name
        case e: ScImportExpr if (e.selectorSet == None && !e.singleWildcard) => new CollectAllProcessor(kinds, refName)
        case e: ScImportExpr if e.singleWildcard => new ResolveProcessor(kinds, refName)
        case _: ScImportSelector => new CollectAllProcessor(kinds, refName)

        case _ => new ResolveProcessor(kinds, refName)
      }
      _resolve(ref, proc)
    }
  }

  def getKinds(incomplete: Boolean) = {
    import StdKinds._
    getParent match {
      case _: ScStableCodeReferenceElement => stableQualRef
      case e: ScImportExpr => if (e.selectorSet != None
              //import Class._ is not allowed
              || qualifier == None || e.singleWildcard) stableQualRef else stableImportSelector
      case ste: ScSimpleTypeElement => if (incomplete) noPackagesClassCompletion /* todo use the settings to include packages*/
      else if (ste.singleton) stableQualRef else stableClass
      case _: ScTypeAlias => stableClass
      case _: ScConstructorPattern => stableClassOrObject
      case _: ScThisReference | _: ScSuperReference => stableClassOrObject
      case _: ScImportSelector => stableImportSelector
      case _ => stableQualRef
    }
  }

  private def _qualifier() = {
    if (getParent.isInstanceOf[ScImportSelector]) {
      getParent.getParent /*ScImportSelectors*/ .getParent.asInstanceOf[ScImportExpr].reference
    } else pathQualifier
  }

  def _resolve(ref: ScStableCodeReferenceElementImpl, processor: BaseProcessor): Array[ResolveResult] = {
    _qualifier match {
      case None => {
        def treeWalkUp(place: PsiElement, lastParent: PsiElement) {
          place match {
            case null =>
            case p => {
              if (!p.processDeclarations(processor,
                ResolveState.initial,
                lastParent, ref)) return
              if (!processor.changedLevel) return
              treeWalkUp(place.getContext, place)
            }
          }
        }
        treeWalkUp(ref, null)
      }
      case Some(q: ScStableCodeReferenceElement) => {
        q.bind match {
          case None =>
          case Some(ScalaResolveResult(typed: ScTyped, s)) => processor.processType(s.subst(typed.calcType), this)
          case Some(ScalaResolveResult(pack: PsiPackage, _)) if pack.getQualifiedName == "scala" => {
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
      case Some(thisQ: ScThisReference) => processor.processType(thisQ.getType, this)
      case Some(superQ: ScSuperReference) => ResolveUtils.processSuperReference(superQ, processor, this)
    }
    processor.candidates.filter(srr => srr.element match {
      case c: PsiClass if c.getName == c.getQualifiedName => c.getContainingFile match {
        case s: ScalaFile => true // scala classes are available from default package
        // Other clases from default package are available only for top-level Scala statmenets
        case _ => PsiTreeUtil.getParentOfType(this, classOf[ScPackaging], true) == null && (getContainingFile match {
          case s : ScalaFile => s.getPackageName.length == 0
          case _ => true
        })
      }
      case _ => true
    }).toArray[ResolveResult]
  }

  def multiResolve(incomplete: Boolean) = {
    getManager.asInstanceOf[PsiManagerEx].getResolveCache.resolveWithCaching(this, MyResolver, true, incomplete)
  }

  def nameId: PsiElement = findChildByType(ScalaTokenTypes.tIDENTIFIER)

  //  @throws(IncorrectOperationException)
  def bindToElement(element: PsiElement): PsiElement = {
    if (isReferenceTo(element)) return this
    else element match {
      case c: PsiClass => {
        if (!ResolveUtils.kindMatches(element, getKinds(false)))
          throw new IncorrectOperationException("class does not match expected kind")
        if (nameId.getText != c.getName)
          throw new IncorrectOperationException("class does not match expected name")
        val qname = c.getQualifiedName
        if (qname != null) getContainingFile match {
          case file: ScalaFile => file.addImportForClass(c) //todo: correct handling
        }
        this
      }
      case _ => throw new IncorrectOperationException("Cannot bind to anything but class")
    }
  }

  def getSameNameVariants: Array[Object] = _resolve(this, new SameNameCompletionProcessor(getKinds(true), refName)).map(r => r.getElement)  
}