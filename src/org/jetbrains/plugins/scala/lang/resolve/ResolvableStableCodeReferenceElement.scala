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
import psi.api.base._
import psi.types._
import resolve._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import com.intellij.psi._
import com.intellij.psi.impl._
import com.intellij.psi.PsiElement
import api.toplevel.ScTypedDefinition
import api.expr.{ScSuperReference, ScThisReference}
import result.TypingContext

trait ResolvableStableCodeReferenceElement extends ScStableCodeReferenceElement {
  private object Resolver extends StableCodeReferenceElementResolver(this)

  def multiResolve(incomplete: Boolean) = {
    getManager.asInstanceOf[PsiManagerEx].getResolveCache.resolveWithCaching(this, Resolver, true, incomplete)
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
                    obj.processDeclarations(processor, ResolveState.initial.put(ScSubstitutor.key, r.substitutor), null, ResolvableStableCodeReferenceElement.this)) {
              // Treat other declarations from package
              pack.processDeclarations(processor, ResolveState.initial.put(ScSubstitutor.key, r.substitutor), null, ResolvableStableCodeReferenceElement.this)

            }
          }
          case Some(other) => {
            other.element.processDeclarations(processor, ResolveState.initial.put(ScSubstitutor.key, other.substitutor),
              null, ResolvableStableCodeReferenceElement.this)
          }
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