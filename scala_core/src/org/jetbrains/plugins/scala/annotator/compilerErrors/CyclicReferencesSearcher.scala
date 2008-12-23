package org.jetbrains.plugins.scala.annotator.compilerErrors

import _root_.org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.{Annotator, AnnotationHolder}
import lang.psi.api.base.ScReferenceElement
import lang.psi.api.base.types.ScTypeInferenceResult
import lang.psi.api.statements.{ScTypeAliasDefinition, ScTypeAlias}
import lang.psi.api.toplevel.ScNamedElement
import lang.psi.types.ScProjectionType

/**
 * @author ilyas
 */

trait CyclicReferencesSearcher {
  this: Annotator =>

  protected def checkCyclicTypeAliases(ref: ScReferenceElement, holder: AnnotationHolder): Unit =
    ref.bind match {
      case Some(ScalaResolveResult(alias: ScTypeAliasDefinition, _)) => {
        alias.aliasedType(Set[ScNamedElement]()) match {
          case ScTypeInferenceResult(_, true, Some(ta)) => {
            val error = ScalaBundle.message("cyclic.reference.type", ta.getName)
            val annotation = holder.createErrorAnnotation(ref.nameId, error)
            annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
          }
          //todo check projection types
          case _ =>
        }
      }
      case _ =>
    }

}