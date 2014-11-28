package org.jetbrains.plugins.hocon.ref

import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider
import com.intellij.psi.{PsiElement, PsiReference}
import org.jetbrains.plugins.hocon.psi.HString
import org.jetbrains.plugins.hocon.settings.HoconProjectSettings

class HStringJavaClassReferenceProvider extends JavaClassReferenceProvider {

  import org.jetbrains.plugins.hocon.lexer.HoconTokenType._
  import org.jetbrains.plugins.hocon.parser.HoconElementType._

  setSoft(true)

  private def isEligible(element: HString) = {
    val settings = HoconProjectSettings.getInstance(element.getProject)
    lazy val parentElementType = element.getParent.getNode.getElementType

    element.stringType match {
      case UnquotedString =>
        // do not detect Java class references on unquoted strings in keys
        settings.classReferencesOnUnquotedStrings && parentElementType != Key
      case QuotedString =>
        // do not detect Java class references on include targets
        settings.classReferencesOnQuotedStrings && parentElementType != Included
      case _ =>
        false
    }
  }

  override def getReferencesByString(str: String, position: PsiElement, offsetInPosition: Int) = position match {
    case hstr: HString if isEligible(hstr) =>
      super.getReferencesByString(str, position, offsetInPosition)
    case _ =>
      PsiReference.EMPTY_ARRAY
  }
}
