package org.jetbrains.plugins.scala.lang.psi.implicits

import api.expr.ScExpression
import api.statements.ScFunctionDefinition
import collection.mutable.{HashMap, HashSet}
import com.intellij.psi.impl.PsiManagerEx
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

  def collectImplicitTypes : List[ScType] = {
      buildImplicitMap.keySet.toList
  }

  def buildImplicitMap = {
    val processor = new CollectImplictisProcessor

    // Collect implicit conversions from botom to up
    def treeWalkUp(place: PsiElement, lastParent: PsiElement) {
      place match {
        case null =>
        case p => {
          if (!p.processDeclarations(processor,
            ResolveState.initial,
            lastParent, this)) return
          if (!processor.changedLevel) return
          treeWalkUp(place.getParent, place)
        }
      }
    }
    treeWalkUp(this, null)

    val sigsFound = processor.signatures.filter((sig: Signature) => {
      val types = sig.types
      types.length == 1 && getType.conforms(types(0))
    })

    val result = new HashMap[ScType, Set[ScFunctionDefinition]]

    for (signature <- sigsFound) {
      val set = processor.sig2Method(signature)
      for (fun <- set) {
        val rt = fun.returnType
        if (!result.contains(rt)) {
          result += (rt -> Set(fun))
        } else {
          result += (rt -> (result(rt) + fun))
        }
      }
    }

    result

  }


  import ResolveTargets._
  class CollectImplictisProcessor extends BaseProcessor(Set(METHOD)) {
    private val signatures2ImplicitMethods = new HashMap[Signature, Set[ScFunctionDefinition]]

    def signatures = signatures2ImplicitMethods.keySet.toArray[Signature]

    def sig2Method = signatures2ImplicitMethods

    def execute(element: PsiElement, state: ResolveState) = {
      def substitutor: ScSubstitutor = {
        state.get(ScSubstitutor.key) match {
          case null => ScSubstitutor.empty
          case x => x
        }
      }
      element match {
        case named: PsiNamedElement if kindMatches(element) => named match {
          case f: ScFunctionDefinition
            // Collect implicit conversions only
            if f.hasModifierProperty("implicit") &&
                    f.getParameterList.getParametersCount == 1 => {
            val sign = new PhysicalSignature(f, substitutor)
            if (!signatures2ImplicitMethods.contains(sign)) {
              val newFSet = Set(f)
              signatures2ImplicitMethods += (sign -> newFSet)
            } else {
              signatures2ImplicitMethods += (sign -> (signatures2ImplicitMethods(sign) + f))
            }
            candidatesSet += new ScalaResolveResult(f)
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