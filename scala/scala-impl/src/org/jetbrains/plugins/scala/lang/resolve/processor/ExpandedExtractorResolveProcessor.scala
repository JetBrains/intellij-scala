package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType
import org.jetbrains.plugins.scala.lang.psi.types.result._

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
        extends ExtractorResolveProcessor(ref, refName, kinds, expected) {

  override protected def execute(namedElement: PsiNamedElement)
                                (implicit state: ResolveState): Boolean = {
    if (nameMatches(namedElement)) {
      val accessible = isAccessible(namedElement, ref)
      if (accessibility && !accessible) return true
      namedElement match {
        case bind: ScTypedDefinition => {
          val parentSubst = getSubst(state)
          val parentImports = getImports(state)
          val typez = getFromType(state) match {
            case Some(tp) => ScProjectionType(tp, bind)
            case _ => bind.`type`().getOrAny
          }
          var seq = false
          val buffer = new ArrayBuffer[ScalaResolveResult]
          val proc = new BaseProcessor(StdKinds.methodRef) {

            override protected def execute(namedElement: PsiNamedElement)
                                          (implicit state: ResolveState): Boolean = {
              namedElement match {
                case fun: ScFunction if fun.name == "unapply" || (seq && fun.name == "unapplySeq") =>
                  buffer += new ScalaResolveResult(fun,
                    parentSubst.followed(getSubst(state)), parentImports, parentElement = Some(bind),
                    isAccessible = accessible)
                case _ =>
              }
              true
            }
          }
          proc.processType(parentSubst(typez), ref, ResolveState.initial)
          addResults(buffer)
          if (candidatesSet.isEmpty && levelSet.isEmpty) {
            buffer.clear()
            seq = true
            proc.processType(parentSubst(typez), ref, ResolveState.initial)
            addResults(buffer)
          }
        }
        case _ => return super.execute(namedElement)
      }
    }
    true
  }
}

