package org.jetbrains.plugins.scala.lang.psi.implicits

import api.expr.ScExpression
import api.statements.{ScFunction, ScFunctionDefinition}
import api.toplevel.imports.usages.ImportUsed
import caches.CachesUtil
import collection.mutable.{HashMap, HashSet}
import com.intellij.openapi.util.Key
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.util.{PsiTreeUtil, CachedValue, PsiModificationTracker}
import com.intellij.psi.{ResolveResult, PsiNamedElement, ResolveState, PsiElement}
import resolve.{ScalaResolveResult, ResolveTargets, BaseProcessor}

import types._

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

  private def implicitMap: collection.Map[ScType, Set[(ScFunctionDefinition, Set[ImportUsed])]] = CachesUtil.get(
      this, ScImplicitlyConvertible.IMPLICIT_CONVERIONS_KEY,
      new CachesUtil.MyProvider(this, {ic: ScImplicitlyConvertible => ic.buildImplicitMap})
        (PsiModificationTracker.MODIFICATION_COUNT)
    )


  private def buildImplicitMap : collection.Map[ScType, Set[(ScFunctionDefinition, Set[ImportUsed])]] = {
    val processor = new CollectImplicitsProcessor(cachedType)

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

    val typez: ScType = cachedType
    val sigsFound = processor.signatures.filter((sig: Signature) => {
      val types = sig.types
      types.length == 1 && typez.conforms(types(0))
    })

    val result = new HashMap[ScType, Set[(ScFunctionDefinition, Set[ImportUsed])]]

    //to prevent infinite recursion
    val functionContext = PsiTreeUtil.getContextOfType(this, classOf[ScFunction], false)

    for (sig <- sigsFound if (sig match {case ps: PhysicalSignature => ps.method != functionContext; case _ => true})) {
      val set = processor.sig2Method(sig)
      for ((imports, fun) <- set) {
        val rt = sig.substitutor.subst(fun.returnType)
        if (!result.contains(rt)) {
          result += (rt -> Set((fun, imports)))
        } else {
          result += (rt -> (result(rt) + (Pair(fun, imports))))
        }
      }
    }
    //todo cache value!
    result
  }


  import ResolveTargets._
  class CollectImplicitsProcessor(val eType: ScType) extends BaseProcessor(Set(METHOD)) {
    private val signatures2ImplicitMethods = new HashMap[Signature, Set[Pair[Set[ImportUsed], ScFunctionDefinition]]]

    def signatures = signatures2ImplicitMethods.keySet.toArray[Signature]

    def sig2Method = signatures2ImplicitMethods

    def execute(element: PsiElement, state: ResolveState) = {

      val implicitSubstitutor = new ScSubstitutor {
        override def subst(t: ScType): ScType = t match {
          case tpt: ScTypeParameterType => eType
          case _ => super.subst(t)
        }

        override def followed(s: ScSubstitutor): ScSubstitutor = s
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
              signatures2ImplicitMethods += (sign -> newFSet)
            } else {
              signatures2ImplicitMethods += (sign -> (signatures2ImplicitMethods(sign) + Pair(getImports(state), f)))
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
  val IMPLICIT_CONVERIONS_KEY: Key[CachedValue[collection.Map[ScType, Set[(ScFunctionDefinition, Set[ImportUsed])]]]] = Key.create("implicit.conversions.key")
}
