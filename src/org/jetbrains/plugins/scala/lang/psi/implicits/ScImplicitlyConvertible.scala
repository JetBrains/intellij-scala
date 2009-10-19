package org.jetbrains.plugins.scala
package lang
package psi
package implicits

import api.expr.ScExpression
import api.statements.{ScFunction, ScFunctionDefinition}
import api.toplevel.imports.usages.ImportUsed
import caches.CachesUtil
import collection.mutable.{HashMap, HashSet}
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.util.{PsiTreeUtil, CachedValue, PsiModificationTracker}
import com.intellij.psi.{ResolveResult, PsiNamedElement, ResolveState, PsiElement}
import resolve.{ScalaResolveResult, ResolveTargets, BaseProcessor}

import types._
import _root_.scala.collection.Set

/**
 * @author ilyas
 *
 * Mix-in implementing functionality to collect [and possibly apply] implicit conversions
 */

trait ScImplicitlyConvertible extends ScalaPsiElement {
  self: ScExpression =>

  /**
   * Get all implicit types for given expression
   */
  def getImplicitTypes : List[ScType] = {
      implicitMap.keySet.toList
  }

  /**
   * Get all imports used to obtain implicit conversions for given type
   */
  def getImportsForImplicit(t: ScType): Set[ImportUsed] = implicitMap.get(t).map(s => s.flatMap(p => p._2.toList)) match {
    case Some(s) => s
    case None => Set()
  }

  @volatile
  private var cachedImplicitMap: collection.Map[ScType, Set[(ScFunctionDefinition, Set[ImportUsed])]] = null

  @volatile
  private var modCount: Long = 0

  private def implicitMap: collection.Map[ScType, Set[(ScFunctionDefinition, Set[ImportUsed])]] = {
    var tp = cachedImplicitMap
    val curModCount = getManager.getModificationTracker.getModificationCount
    if (tp != null && modCount == curModCount) {
      return tp
    }
    tp = buildImplicitMap
    cachedImplicitMap = tp
    modCount = curModCount
    return tp
  }

  private def buildImplicitMap : collection.Map[ScType, Set[(ScFunctionDefinition, Set[ImportUsed])]] = {
    val processor = new CollectImplicitsProcessor(getType)

    // Collect implicit conversions from bottom to up
    def treeWalkUp(place: PsiElement, lastParent: PsiElement) {
      place match {
        case null =>
        case p => {
          if (!p.processDeclarations(processor,
            ResolveState.initial,
            lastParent, this)) return
          if (!processor.changedLevel) return
          treeWalkUp(place.getContext, place)
        }
      }
    }
    treeWalkUp(this, null)

    val typez: ScType = getType
    val result = new HashMap[ScType, Set[(ScFunctionDefinition, Set[ImportUsed])]]
    if (typez == Nothing) return result
    
    val sigsFound = processor.signatures.filter((sig: Signature) => {
      ProgressManager.getInstance().checkCanceled()
      val types = sig.types
      types.length == 1 && typez.conforms(sig.substitutor.subst(types(0)))
    })

    //to prevent infinite recursion
    val functionContext = PsiTreeUtil.getContextOfType(this, classOf[ScFunction], false)

    for (sig <- sigsFound if (sig match {case ps: PhysicalSignature => ps.method != functionContext; case _ => true})) {
      val set = processor.sig2Method(sig)
      for ((imports, fun) <- set) {
        val rt = sig.substitutor.subst(fun.returnType.unwrap(Any))

        def register(t: ScType) = {
          if (!result.contains(t)) {
            result += (t -> Set((fun, imports)))
          } else {
            result += (t -> (result(t) + (Pair(fun, imports))))
          }
        }
        rt match {
          // This is needed to pass OptimizeImportsImplicitsTest.testImplicitReference2
          case ct: ScCompoundType => {
            register(ct)
            for (t <- ct.components)
              register(t)
          }
          case t => register(t)
        }
      }
    }
    result
  }


  import ResolveTargets._
  class CollectImplicitsProcessor(val eType: ScType) extends BaseProcessor(Set(METHOD)) {
    private val signatures2ImplicitMethods = new HashMap[Signature, Set[Pair[Set[ImportUsed], ScFunctionDefinition]]]

    def signatures = signatures2ImplicitMethods.keySet.toArray[Signature]

    def sig2Method = signatures2ImplicitMethods

    def execute(element: PsiElement, state: ResolveState) = {

      val implicitSubstitutor = new ScSubstitutor {
        override protected def substInternal(t: ScType): ScType = {
          t match {
            case tpt: ScTypeParameterType => eType
            case _ => super.substInternal(t)
          }
        }
      }

      element match {
        case named: PsiNamedElement if kindMatches(element) => named match {
          case f: ScFunctionDefinition
            // Collect implicit conversions only
            if f.hasModifierProperty("implicit") &&
                    f.getParameterList.getParametersCount == 1 => {
            val sign = new PhysicalSignature(f, implicitSubstitutor)
            if (!signatures2ImplicitMethods.contains(sign)) {
              val newFSet = Set((getImports(state), f))
              signatures2ImplicitMethods += ((sign -> newFSet))
            } else {
              signatures2ImplicitMethods += ((sign -> (signatures2ImplicitMethods(sign) + Pair(getImports(state), f))))
            }
            candidatesSet += new ScalaResolveResult(f, getSubst(state), getImports(state))
          }
          //todo add implicit objects
          case _ =>
        }
        case _ =>
      }
      true

    }
  }

  protected object MyImplicitCollector {
  }

}

object ScImplicitlyConvertible {
  val IMPLICIT_CONVERSIONS_KEY: Key[CachedValue[collection.Map[ScType, Set[(ScFunctionDefinition, Set[ImportUsed])]]]] = Key.create("implicit.conversions.key")
}
