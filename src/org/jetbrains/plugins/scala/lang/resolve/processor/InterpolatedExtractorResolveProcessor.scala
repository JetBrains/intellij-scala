package org.jetbrains.plugins.scala
package lang.resolve.processor

import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import scala.collection.Set
import org.jetbrains.plugins.scala.lang.resolve.{StdKinds, ScalaResolveResult, ResolveTargets}
import org.jetbrains.plugins.scala.lang.psi.types.{ScProjectionType, ScType}
import com.intellij.psi.{PsiNamedElement, ResolveState, PsiElement}
import extensions.toPsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import scala.collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

/**
 * @author kfeodorov 
 * @since 25.03.14.
 */
class InterpolatedExtractorResolveProcessor (ref: ScReferenceElement,
                                             refName: String,
                                             kinds: Set[ResolveTargets.Value],
                                             expected: Option[ScType])
        extends ExpandedExtractorResolveProcessor(ref, refName, kinds, expected) {

  override def nameAndKindMatch(named: PsiNamedElement, state: ResolveState): Boolean = {
    val nameSet = state.get(ResolverEnv.nameKey)
    val elName = if (nameSet == null) {
      val name = named.getName.replace("$","") //nested objects can have trailing $
      if (name == null) return false
      if (name == "") return false
      name
    } else  nameSet
    val nameMatches = ScalaPsiUtil.memberNamesEquals(elName, name)
    nameMatches && kindMatches(named)
  }
}
