package org.jetbrains.plugins.scala.editor.documentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

/*
 * - quick info: escape < & > for type parameters in the beginning, middle, end, SCL-7725
 */
class ScalaDocumentationProviderQuickInfoTest extends ScalaDocumentationProviderTestBase {

  override protected def generateDoc(editor: Editor, file: PsiFile): String = {
    val (referredElement, elementAtCaret) = extractReferredAndOriginalElements(editor, file)
    documentationProvider.getQuickNavigateInfo(referredElement, elementAtCaret)
  }

  def testEscapeGenericsBounds(): Unit =
    doGenerateDocTest(
      s"""trait Trait[A]
         |abstract class ${|}Class[T <: Trait[_ >: Object]]
         |  extends Comparable[_ <: Trait[_ >: String]]""".stripMargin,
      s"""[${getModule.getName}] default
        |abstract class <a href="psi_element://Class"><code>Class</code></a>[T &lt;:
        | <a href="psi_element://Trait"><code>Trait</code></a>[_ &gt;:
        | <a href="psi_element://java.lang.Object"><code>Object</code></a>]]
        | extends <a href="psi_element://java.lang.Comparable"><code>Comparable</code></a>[_ &lt;:
        | <a href="psi_element://Trait"><code>Trait</code></a>[_ &gt;:
        | <a href="psi_element://scala.Predef.String"><code>String</code></a>]]""".stripMargin
    )
}