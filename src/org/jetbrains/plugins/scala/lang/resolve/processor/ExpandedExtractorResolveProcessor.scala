package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import processor.ExtractorResolveProcessor
import psi.api.base.ScReferenceElement
import psi.api.statements._
import com.intellij.psi._
import psi.types._

import result.{TypingContext}
import scala._
import collection.mutable.{HashSet, ListBuffer, ArrayBuffer}
import collection.{Seq, Set}
import psi.implicits.{ScImplicitlyConvertible}
import psi.api.base.patterns.{ScBindingPattern}
import psi.ScalaPsiUtil
import psi.api.toplevel.typedef.{ScTypeDefinition}
import psi.api.toplevel.imports.usages.{ImportUsed}
/**
 * This class is useful for finding actual methods for unapply or unapplySeq, in case for values:
 * <code>
 *   val a: Regex
 *   z match {
 *     case a() =>
 *   }
 * </code>
 * This class cannot be used for actual resolve, because reference to value should work to this value, not to
 * invoked unapply method.
 */
class ExpandedExtractorResolveProcessor(ref: ScReferenceElement,
                                        refName: String, kinds: Set[ResolveTargets.Value],
                                        expected: Option[ScType]) 
        extends ExtractorResolveProcessor(ref, refName, kinds, expected) {

  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (nameAndKindMatch(named, state)) {
      if (!isAccessible(named, ref)) return false
      named match {
        case bind: ScBindingPattern => {
          ScalaPsiUtil.nameContext(bind) match {
            case v: ScValue => {
              val parentSubst = getSubst(state)
              val typez = bind.getType(TypingContext.empty).getOrElse(return true)
              ScType.extractClassType(typez) match {
                case Some((clazz: ScTypeDefinition, substitutor: ScSubstitutor)) => {
                  for (sign <- clazz.signaturesByName("unapply")) {
                    val m = sign.method
                    val subst = sign.substitutor
                    candidatesSet += new ScalaResolveResult(m, parentSubst.followed(substitutor).followed(subst),
                      getImports(state))
                  }
                  //unapply has bigger priority then unapplySeq
                  if (candidatesSet.isEmpty)
                  for (sign <- clazz.signaturesByName("unapplySeq")) {
                    val m = sign.method
                    val subst = sign.substitutor
                    candidatesSet += new ScalaResolveResult(m, parentSubst.followed(substitutor).followed(subst),
                      getImports(state))
                  }
                  return true
                }
                case _ => return true
              }
            }
            case _ => return true
          }
        }
        case _ => return super.execute(element, state)
      }
    }
    true
  }
}
