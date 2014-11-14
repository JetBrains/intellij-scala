package intellijhocon
package ref

import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider
import com.intellij.psi.{PsiElement, PsiReference}
import psi.HString
import settings.HoconProjectSettings

class HStringJavaClassReferenceProvider extends JavaClassReferenceProvider {

  import intellijhocon.lexer.HoconTokenType._
  import intellijhocon.parser.HoconElementType._

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
