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
  //  self: ScExpression =>

  def collectImplicitSignatures = {
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

    processor.candidates.map((res: ResolveResult) => res.getElement);


    //    val results = getManager.asInstanceOf[PsiManagerEx].getResolveCache.resolveWithCaching(this, MyImplicitCollector, true, true)
    ; //todo map results to elements
  }


  import ResolveTargets._
  class CollectImplictisProcessor extends BaseProcessor(Set(METHOD)) {
    private val signatures2ImplicitMethods = new HashMap[Signature, Set[ScFunctionDefinition]]

    def signatures = signatures2ImplicitMethods.keySet.toArray[Signature]

    def execute(element: PsiElement, state: ResolveState) = {
      def substitutor: ScSubstitutor = {
        state.get(ScSubstitutor.key) match {
          case null => ScSubstitutor.empty
          case x => x
        }
      }
      element match {
        case named: PsiNamedElement if kindMatches(element) => named match {
          case f: ScFunctionDefinition if f.hasModifierProperty("implicit") => {
            val sign = new PhysicalSignature(f, substitutor)
            val set = signatures2ImplicitMethods(sign)
            if (set == null) {
              val newFSet = Set(f)
              signatures2ImplicitMethods += (sign -> newFSet)
            } else {
              signatures2ImplicitMethods += (sign -> (set + f))
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