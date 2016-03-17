package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

import scala.collection.Set
import scala.collection.mutable.ArrayBuffer
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
                                       (implicit override val typeSystem: TypeSystem)
        extends ExtractorResolveProcessor(ref, refName, kinds, expected) {
  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    if (nameAndKindMatch(named, state)) {
      val accessible = isAccessible(named, ref)
      if (accessibility && !accessible) return true
      named match {
        case bind: ScTypedDefinition => {
          val parentSubst = getSubst(state)
          val parentImports = getImports(state)
          val typez = getFromType(state) match {
            case Some(tp) => ScProjectionType(tp, bind, superReference = false)
            case _ => bind.getType(TypingContext.empty).getOrAny
          }
          var seq = false
          val buffer = new ArrayBuffer[ScalaResolveResult]
          val proc = new BaseProcessor(StdKinds.methodRef) {
            def execute(element: PsiElement, state: ResolveState): Boolean = {
              val subst = getSubst(state)
              element match {
                case fun: ScFunction if fun.name == "unapply" || (seq && fun.name == "unapplySeq") =>
                  buffer += new ScalaResolveResult(fun,
                    parentSubst.followed(subst), parentImports, parentElement = Some(bind),
                    isAccessible = accessible)
                case _ =>
              }
              true
            }
          }
          proc.processType(parentSubst.subst(typez), ref, ResolveState.initial)
          addResults(buffer.toSeq)
          if (candidatesSet.isEmpty && levelSet.isEmpty) {
            buffer.clear()
            seq = true
            proc.processType(parentSubst.subst(typez), ref, ResolveState.initial)
            addResults(buffer.toSeq)
          }
        }
        case _ => return super.execute(element, state)
      }
    }
    true
  }
}

