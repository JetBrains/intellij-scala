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
    (element.getNode.getElementType, element.stringType) match {
      case (StringValue, UnquotedString) => settings.classReferencesOnUnquotedStrings
      case (StringValue | KeyPart, QuotedString) => settings.classReferencesOnQuotedStrings
      case _ => false
    }
  }

  override def getReferencesByString(str: String, position: PsiElement, offsetInPosition: Int): Array[PsiReference] = position match {
    case hstr: HString if isEligible(hstr) =>
      super.getReferencesByString(str, position, offsetInPosition)
    case _ =>
      PsiReference.EMPTY_ARRAY
  }
}
