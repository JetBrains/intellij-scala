package intellijhocon
package ref

import com.intellij.psi.{PsiReference, PsiElement}
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider
import psi.HString

class HStringJavaClassReferenceProvider extends JavaClassReferenceProvider {

  import intellijhocon.lexer.HoconTokenType._
  import intellijhocon.parser.HoconElementType._

  private def isEligible(element: HString) = element.stringType match {
    case QuotedString =>
      // do not detect Java class references on include targets
      element.getParent.getNode.getElementType != Included
    case UnquotedString =>
      // do not detect Java class references on unquoted strings in keys
      element.getParent.getNode.getElementType != Key
    case _ =>
      true
  }

  override def getReferencesByString(str: String, position: PsiElement, offsetInPosition: Int) = position match {
    case hstr: HString if isEligible(hstr) =>
      super.getReferencesByString(str, position, offsetInPosition)
    case _ =>
      PsiReference.EMPTY_ARRAY
  }
}
