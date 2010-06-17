package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang._
import processor.{CompletionProcessor, BaseProcessor}
import psi.api.base._
import psi.api.expr._
import psi.api.ScalaFile
import psi.api.toplevel.ScTypedDefinition
import psi.api.toplevel.typedef.{ScObject, ScTrait, ScTypeDefinition, ScClass}
import psi.impl.toplevel.synthetic.SyntheticClasses
import psi.api.toplevel.packaging.ScPackaging
import psi.ScalaPsiUtil
import psi.types._
import resolve._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import com.intellij.psi._
import com.intellij.psi.impl._
import com.intellij.psi.PsiElement
import result.TypingContext
import caches.{CachesUtil, ScalaCachesManager}

trait ResolvableStableCodeReferenceElement extends ScStableCodeReferenceElement {
  private object Resolver extends StableCodeReferenceElementResolver(this)

  def multiResolve(incomplete: Boolean) = {
    getManager.asInstanceOf[PsiManagerEx].getResolveCache.resolveWithCaching(this, Resolver, true, incomplete)
  }

  def processQualifierResolveResult(res: ResolveResult, processor: BaseProcessor, ref: ScStableCodeReferenceElement): Unit = {
    res match {
      case ScalaResolveResult(td: ScTypeDefinition, substitutor) => {
        td match {
          case _: ScObject => {
            td.processDeclarations(processor, ResolveState.initial.put(ScSubstitutor.key, substitutor),
              null, ResolvableStableCodeReferenceElement.this)
            //todo: hack, remove after decompiler fix
            ref.getContainingFile match {
              case sc: ScalaFile if sc.isCompiled => {
                val candidates = processor.candidates.filter(candidatesFilter)
                if (candidates.isEmpty) {
                  ScalaPsiUtil.getCompanionModule(td) match {
                    case Some(cl) => {
                      import _root_.java.lang.Boolean
                      cl.processDeclarations(processor, ResolveState.initial.
                            put(ScSubstitutor.key, substitutor).put(CachesUtil.HACKED_KEY, Boolean.TRUE),
                      null, ResolvableStableCodeReferenceElement.this)
                    }
                    case _ =>
                  }
                }
              }
              case _ =>
            }
          }
          case _: ScClass | _: ScTrait => td.processDeclarations(processor, ResolveState.initial.put(ScSubstitutor.key, substitutor),
            null, ResolvableStableCodeReferenceElement.this)
        }
      }
      case ScalaResolveResult(typed: ScTypedDefinition, s) =>
        processor.processType(s.subst(typed.getType(TypingContext.empty).getOrElse(Any)), this)
      case other: ScalaResolveResult => {
        other.element.processDeclarations(processor, ResolveState.initial.put(ScSubstitutor.key, other.substitutor),
          null, ResolvableStableCodeReferenceElement.this)
      }
      case _ =>
    }
  }

  def doResolve(ref: ScStableCodeReferenceElement, processor: BaseProcessor): Array[ResolveResult] = {
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
        val results = q.bind match {
          case Some(res) => processQualifierResolveResult(res, processor, ref)
          case _ =>
        }
      }
      case Some(thisQ: ScThisReference) => for (ttype <- thisQ.getType(TypingContext.empty)) processor.processType(ttype, this)
      case Some(superQ: ScSuperReference) => ResolveUtils.processSuperReference(superQ, processor, this)
    }

    val candidates = processor.candidates
    val filtered = candidates.filter(candidatesFilter)

    filtered.toArray
  }

  private def _qualifier() = {
    getContext match {
      case sel: ScImportSelector => {
        sel.getContext /*ScImportSelectors*/.getContext.asInstanceOf[ScImportExpr].reference
      }
      case _ => pathQualifier
    }
  }

  private def candidatesFilter(result: ScalaResolveResult) = {
    result.element match {
      case c: PsiClass if c.getName == c.getQualifiedName => c.getContainingFile match {
        case s: ScalaFile => true // scala classes are available from default package
        // Other classes from default package are available only for top-level Scala statements
        case _ => PsiTreeUtil.getContextOfType(this, classOf[ScPackaging], true) == null && (getContainingFile match {
          case s: ScalaFile => s.getPackageName.length == 0
          case _ => true
        })
      }
      case _ => true
    }
  }
}