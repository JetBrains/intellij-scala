package org.jetbrains.plugins.scala
package lang
package psi
package implicits

import api.expr.ScExpression
import api.statements.{ScFunction, ScFunctionDefinition}
import api.toplevel.imports.usages.ImportUsed
import caches.CachesUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.util.{PsiTreeUtil, CachedValue, PsiModificationTracker}
import lang.resolve.{ScalaResolveResult, ResolveTargets, BaseProcessor}

import types._
import _root_.scala.collection.Set
import result.TypingContext
import api.statements.params.ScTypeParam
import com.intellij.psi._
import collection.mutable.{ArrayBuffer, HashMap, HashSet}

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
   * returns class which contains function for implicit conversion to type t.
   */
  def getClazzForType(t: ScType): Option[PsiClass] = {
    implicitMap.get(t) match {
      case Some(set: Set[(ScFunctionDefinition, Set[ImportUsed])]) if set.size == 1 => return {
        set.toSeq.apply(0)._1.getContainingClass match {
          case null => None
          case x => Some(x)
        }
      }
      case _ => None
    }
  }

  /**
   *  Get all imports used to obtain implicit conversions for given type
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
    val processor = new CollectImplicitsProcessor

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

    val typez: ScType = getType(TypingContext.empty).getOrElse(Nothing)
    val result = new HashMap[ScType, Set[(ScFunctionDefinition, Set[ImportUsed])]]
    if (typez == Nothing) return result
    if (typez.isInstanceOf[ScUndefinedType]) return result
    
    val sigsFound = processor.signatures.filter((sig: Signature) => {
      ProgressManager.checkCanceled
      val types = sig.types
      types.length == 1 && typez.conforms(sig.substitutor.subst(types(0)))
    }).map((sig: Signature) => sig match {
      case phys: PhysicalSignature => {
        val uSubst = Conformance.undefinedSubst(sig.substitutor.subst(sig.types.apply(0)), typez)  //todo: add missed implicit params
        uSubst.getSubstitutor match {
          case Some(s) =>  new PhysicalSignature(phys.method, phys.substitutor.followed(s))
          case _ => sig
        }
      }
      case _ => sig
    })

    //to prevent infinite recursion
    val functionContext = PsiTreeUtil.getContextOfType(this, classOf[ScFunction], false)

    for (sig <- sigsFound if (sig match {case ps: PhysicalSignature => ps.method != functionContext; case _ => false})) {
      val set = processor.sig2Method(sig match {case ps: PhysicalSignature => ps.method.asInstanceOf[ScFunctionDefinition]})
      for ((imports, fun) <- set) {
        val rt = sig.substitutor.subst(fun.returnType.getOrElse(Any))

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
  private class CollectImplicitsProcessor extends BaseProcessor(Set(METHOD)) {
    private val signatures2ImplicitMethods = new HashMap[ScFunctionDefinition, Set[Pair[Set[ImportUsed], ScFunctionDefinition]]]

    private val signaturesSet: HashSet[Signature] = new HashSet[Signature] //signatures2ImplicitMethods.keySet.toArray[Signature]

    def signatures: Array[Signature] = signaturesSet.toArray

    def sig2Method = signatures2ImplicitMethods

    def execute(element: PsiElement, state: ResolveState) = {

      val subst: ScSubstitutor = state.get(ScSubstitutor.key) match {
        case null => ScSubstitutor.empty
        case s => s
      }

      element match {
        case named: PsiNamedElement if kindMatches(element) => named match {
          case f: ScFunctionDefinition
            // Collect implicit conversions only
            if f.hasModifierProperty("implicit") &&
                    f.getParameterList.getParametersCount == 1 => {
            val sign = new PhysicalSignature(f, subst.followed(inferMethodTypesArgs(f, subst)))
            if (!signatures2ImplicitMethods.contains(f)) {
              val newFSet = Set((getImports(state), f))
              signatures2ImplicitMethods += ((f -> newFSet))
              signaturesSet += sign
            } else {
              signatures2ImplicitMethods += ((f -> (signatures2ImplicitMethods(f) + Pair(getImports(state), f))))
              signaturesSet += sign
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

    /**
     Pick all type parameters by method maps them to the appropriate type arguments, if they are
     */
    def inferMethodTypesArgs(fun: ScFunction, classSubst: ScSubstitutor) = {
      fun.typeParameters.foldLeft(ScSubstitutor.empty) {
        (subst, tp) => subst.bindT(tp.getName, ScUndefinedType(new ScTypeParameterType(tp: ScTypeParam, classSubst)))
      }
    }
  }

  protected object MyImplicitCollector {
  }

}

object ScImplicitlyConvertible {
  val IMPLICIT_RESOLUTION_KEY: Key[PsiClass] = Key.create("implicit.resolution.key")
  val IMPLICIT_CONVERSIONS_KEY: Key[CachedValue[collection.Map[ScType, Set[(ScFunctionDefinition, Set[ImportUsed])]]]] = Key.create("implicit.conversions.key")
}
