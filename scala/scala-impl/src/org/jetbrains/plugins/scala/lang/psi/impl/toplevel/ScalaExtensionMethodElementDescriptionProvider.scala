package org.jetbrains.plugins.scala.lang.psi.impl.toplevel

import com.intellij.psi.{ElementDescriptionLocation, ElementDescriptionProvider, PsiElement}
import com.intellij.usageView.{UsageViewLongNameLocation, UsageViewShortNameLocation}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiPresentationUtils.{extensionMethodPresentableText, extensionMethodShortText}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
 * Provides receiver-qualified Scala 3 extension labels to the Usage View,
 * used by Find Usages and Show Usages. Long labels include signature clauses;
 * short labels do not, matching the respective ordinary-method conventions.
 */
final class ScalaExtensionMethodElementDescriptionProvider extends ElementDescriptionProvider {
  override def getElementDescription(element: PsiElement, location: ElementDescriptionLocation): String =
    element match {
      case function: ScFunction if function.isExtensionMethod && location == UsageViewLongNameLocation.INSTANCE =>
        extensionMethodPresentableText(function)
      case function: ScFunction if function.isExtensionMethod && location == UsageViewShortNameLocation.INSTANCE =>
        extensionMethodShortText(function)
      case _ => null
    }
}
