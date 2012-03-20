package org.jetbrains.plugins.scala.lang.completion.lookups

import org.jetbrains.plugins.scala.lang.psi.types.{Nothing, ScType}
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult}
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.extensions.toPsiNamedElementExt

/**
 * @author Alefas
 * @since 19.03.12
 */
object LookupElementManager {
  def getLookup(resolveResult: ScalaResolveResult,
                qualifierType: ScType = Nothing,
                isClassName: Boolean = false,
                isInImport: Boolean = false,
                isOverloadedForClassName: Boolean = false,
                shouldImport: Boolean = false,
                isInStableCodeReference: Boolean = false): LookupElement = {
    val lookupString = resolveResult.isRenamed match {
      case Some(lookupString) => lookupString
      case None => resolveResult.getElement.name
    }
    resolveResult.getElement match {
      case method: PsiMethod =>
        return new ScalaMethodLookupElement(method, lookupString)
      case _ =>
    }
    ResolveUtils.getLookupElement(resolveResult, qualifierType, isClassName, isInImport, isOverloadedForClassName,
      shouldImport, isInStableCodeReference).apply(0)._1
  }
}
