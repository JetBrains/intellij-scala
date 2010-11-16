package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import psi.api.base.ScReferenceElement
import psi.api.statements._
import com.intellij.psi._
import psi.types._

import result.TypingContext
import scala._
import collection.Set
import psi.api.toplevel.ScTypedDefinition
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
                                        refName: String,
                                        kinds: Set[ResolveTargets.Value],
                                        expected: Option[ScType])
        extends ExtractorResolveProcessor(ref, refName, kinds, expected) {
  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (nameAndKindMatch(named, state)) {
      if (!isAccessible(named, ref)) return false
      named match {
        case bind: ScTypedDefinition => {
          val parentSubst = getSubst(state)
          val parentImports = getImports(state)
          val typez = bind.getType(TypingContext.empty).getOrElse(return true)
          var seq = false
          val proc = new BaseProcessor(StdKinds.methodRef) {
            def execute(element: PsiElement, state: ResolveState): Boolean = {
              val subst = getSubst(state)
              element match {
                case fun: ScFunction if fun.name == "unapply" || (seq && fun.name == "unapplySeq") =>
                  ExpandedExtractorResolveProcessor.this.addResult(new ScalaResolveResult(fun,
                    parentSubst.followed(subst), parentImports, parentElement = Some(bind)))
                case _ =>
              }
              true
            }
          }
          proc.processType(parentSubst.subst(typez), ref, ResolveState.initial)
          if (candidatesSet.isEmpty && levelSet.isEmpty) {
            seq = true
            proc.processType(parentSubst.subst(typez), ref, ResolveState.initial)
          }
        }
        case _ => return super.execute(element, state)
      }
    }
    true
  }
}
