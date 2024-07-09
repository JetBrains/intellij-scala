package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.lang.documentation.psi.PsiElementDocumentationTarget
import com.intellij.platform.backend.documentation.{DocumentationTarget, PsiDocumentationTargetProvider}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.annotator.gutter.ScalaGoToDeclarationHandler
import org.jetbrains.plugins.scala.extensions.ToNullSafe
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference

import java.util
import scala.jdk.CollectionConverters.SeqHasAsJava

/**
 * See also [[org.jetbrains.plugins.scala.annotator.gutter.ScalaGoToDeclarationHandler.syntheticTarget]]
 */
//noinspection UnstableApiUsage
class ScalaPsiDocumentationTargetProvider extends PsiDocumentationTargetProvider {

  override def documentationTarget(element: PsiElement, originalElement: PsiElement): DocumentationTarget = {
    // By calling `getNavigationElement` we also handle all kinds of synthetic elements.
    // It retrieves corresponding elements from sources instead of decompiled classes of libraries.
    val elementActual = element.getNavigationElement.nullSafe.getOrElse(element)

    if (elementActual.getLanguage.isKindOf(ScalaLanguage.INSTANCE))
      new PsiElementDocumentationTarget(elementActual.getProject, elementActual, originalElement)
    else
      null
  }

  override def documentationTargets(element: PsiElement, originalElement: PsiElement): util.List[DocumentationTarget] = {
    val nonSyntheticTargets = element match {
      case ref: ScReference =>
        // NOTE: in practice this code handles only those references which resolve to multiple elements
        // if a reference under the caret is resolved to a single element,
        // it will be handled by the platform, and we will get the resolved element here
        val results = ref.multiResolveScala(false)
        results.flatMap(r => ScalaGoToDeclarationHandler.syntheticTargetOrSelf(r.element))
          .distinct
          .toSeq
      case _ =>
        Nil
    }
    if (nonSyntheticTargets.isEmpty)
      super.documentationTargets(element, originalElement)
    else {
      val project = element.getProject
      nonSyntheticTargets
        .map(e => new PsiElementDocumentationTarget(project, e.getNavigationElement, originalElement): DocumentationTarget)
        .asJava
    }
  }
}
