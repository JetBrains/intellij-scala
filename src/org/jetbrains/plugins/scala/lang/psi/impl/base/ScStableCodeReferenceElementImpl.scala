package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import api.ScalaFile
import api.toplevel.packaging.ScPackaging
import caches.ScalaCachesManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang._
import lexer.ScalaTokenTypes
import psi.ScalaPsiElementImpl
import psi.api.base._
import psi.types._
import psi.impl.ScalaPsiElementFactory
import resolve._
import com.intellij.psi.impl.source.resolve.ResolveCache
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.impl._
import com.intellij.psi.PsiElement
import com.intellij.util.IncorrectOperationException
import api.toplevel.ScTypedDefinition
import api.statements.ScTypeAlias
import api.base.patterns.ScConstructorPattern
import api.expr.{ScSuperReference, ScThisReference}
import result.TypingContext
import api.base.types.{ScInfixTypeElement, ScSimpleTypeElement}

/**
 * @author AlexanderPodkhalyuzin
 * Date: 22.02.2008
 */

class ScStableCodeReferenceElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScStableCodeReferenceElement {
  def getVariants(): Array[Object] = _resolve(this, new CompletionProcessor(getKinds(true))).map(r => {
    r match {
      case res: ScalaResolveResult => ResolveUtils.getLookupElement(res)
      case _ => r.getElement
    }
  })

  override def toString: String = "CodeReferenceElement"

  object MyResolver extends ResolveCache.PolyVariantResolver[ScStableCodeReferenceElementImpl] {
    def resolve(ref: ScStableCodeReferenceElementImpl, incomplete: Boolean) = {
      val kinds = ref.getKinds(false)
      val proc = ref.getContext match {
      //last ref may import many elements with the same name
        case e: ScImportExpr if (e.selectorSet == None && !e.singleWildcard) => new CollectAllProcessor(kinds, refName)
        case e: ScImportExpr if e.singleWildcard => new ResolveProcessor(kinds, refName)
        case _: ScImportSelector => new CollectAllProcessor(kinds, refName)

        case _ => new ResolveProcessor(kinds, refName)
      }
      _resolve(ref, proc)
    }
  }

  def getKinds(incomplete: Boolean): Set[ResolveTargets.Value] = {
    import StdKinds._
    getContext match {
      case _: ScStableCodeReferenceElement => stableQualRef
      case e: ScImportExpr => if (e.selectorSet != None
              //import Class._ is not allowed
              || qualifier == None || e.singleWildcard) stableQualRef else stableImportSelector
      case ste: ScSimpleTypeElement => if (incomplete) noPackagesClassCompletion /* todo use the settings to include packages*/
      else if (ste.singleton) stableQualRef else stableClass
      case _: ScTypeAlias => stableClass
      case _: ScConstructorPattern => classOrObjectOrValues
      case _: ScThisReference | _: ScSuperReference => stableClassOrObject
      case _: ScImportSelector => stableImportSelector
      case _: ScInfixTypeElement => stableClass
      case _ => stableQualRef
    }
  }

  private def _qualifier() = {
    getContext match {
      case sel: ScImportSelector => {
        sel.getContext /*ScImportSelectors*/.getContext.asInstanceOf[ScImportExpr].reference
      }
      case _ => pathQualifier
    }
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
          case Some(ScalaResolveResult(typed: ScTypedDefinition, s)) => 
            processor.processType(s.subst(typed.getType(TypingContext.empty).getOrElse(Any)), this)
          case Some(r@ScalaResolveResult(pack: PsiPackage, _)) => {

            // Process synthetic classes for scala._ package
            if (pack.getQualifiedName == "scala") {
              import toplevel.synthetic.SyntheticClasses
              for (synth <- SyntheticClasses.get(getProject).getAll) {
                processor.execute(synth, ResolveState.initial)
              }
            }

            // Process package object declarations first
            // Treat package object first
            val manager = ScalaCachesManager.getInstance(getProject)
            val cache = manager.getNamesCache
            val obj = cache.getPackageObjectByName(pack.getQualifiedName, GlobalSearchScope.allScope(getProject))
            if (obj == null ||
                    obj.processDeclarations(processor, ResolveState.initial.put(ScSubstitutor.key, r.substitutor), null, ScStableCodeReferenceElementImpl.this)) {
              // Treat other declarations from package
              pack.processDeclarations(processor, ResolveState.initial.put(ScSubstitutor.key, r.substitutor), null, ScStableCodeReferenceElementImpl.this)

            }
          }
          case Some(other) => {
            other.element.processDeclarations(processor, ResolveState.initial.put(ScSubstitutor.key, other.substitutor),
              null, ScStableCodeReferenceElementImpl.this)
          }
        }
      }
      case Some(thisQ: ScThisReference) => for (ttype <- thisQ.getType(TypingContext.empty)) processor.processType(ttype, this)
      case Some(superQ: ScSuperReference) => ResolveUtils.processSuperReference(superQ, processor, this)
    }
    processor.candidates.filter(srr => srr.element match {
      case c: PsiClass if c.getName == c.getQualifiedName => c.getContainingFile match {
        case s: ScalaFile => true // scala classes are available from default package
        // Other classes from default package are available only for top-level Scala statements
        case _ => PsiTreeUtil.getContextOfType(this, classOf[ScPackaging], true) == null && (getContainingFile match {
          case s : ScalaFile => s.getPackageName.length == 0
          case _ => true
        })
      }
      case _ => true
    }).toArray[ResolveResult]
  }

  object E1 extends Enumeration {
  val x = Value("x")
  val y = Value("y")
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
        if (nameId.getText != c.getName) {
          val ref = ScalaPsiElementFactory.createReferenceFromText(c.getName, getManager)
          getNode.getTreeParent.replaceChild(this.getNode, ref.getNode)
          return ref
        }
        val qname = c.getQualifiedName
        if (qname != null) org.jetbrains.plugins.scala.annotator.intention.
                ScalaImportClassFix.getImportHolder(ref = this, project = getProject).
                addImportForClass(c, ref = this) //todo: correct handling
        this
      }
      case _ => throw new IncorrectOperationException("Cannot bind to anything but class")
    }
  }

  def getSameNameVariants: Array[ResolveResult] = _resolve(this, new CompletionProcessor(getKinds(true))).
          filter(r => r.getElement.isInstanceOf[PsiNamedElement] &&
          r.getElement.asInstanceOf[PsiNamedElement].getName == refName)
}