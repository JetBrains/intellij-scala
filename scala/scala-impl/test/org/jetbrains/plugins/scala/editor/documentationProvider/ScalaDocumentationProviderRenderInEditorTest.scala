package org.jetbrains.plugins.scala.editor.documentationProvider
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScDocCommentOwner
import org.jetbrains.plugins.scala.util.AliasExports._

// TODO: in-editor doc: code example in the end of the doc produces new line
class ScalaDocumentationProviderRenderInEditorTest extends ScalaDocumentationProviderTestBase {

  override protected def generateDoc(referredElement: PsiElement, elementAtCaret: PsiElement): String =
    documentationProvider.generateRenderedDoc(referredElement.asInstanceOf[ScDocCommentOwner].getDocComment)

  def testSingleParagraphInDescription(): Unit =
    doGenerateDocTest(
      s"""class AllTags {
         |  /** description */
         |  def ${|}foo[T](p: String) = 42
         |}
         |""".stripMargin,
      s"""<html>
         |${DocHtmlHead(myFixture.getFile)}
         |$BodyStart
         |$ContentStart
         |description
         |$ContentEnd
         |$BodyEnd
         |</html>
         |""".stripMargin
    )

  def testAllTags_WithoutDescription(): Unit = {
    // extra tags rendered: author and version
    val fileText    =
      s"""class AllTags {
         |  /**
         |   * @see some text
         |   * @author some text
         |   * @note some text
         |   * @since some text
         |   * @define some text
         |   * @version some text
         |   * @todo some text
         |   * @usecase some text
         |   * @example some text
         |   * @deprecated some text
         |   * @migration some text
         |   * @group some text
         |   * @groupname some text
         |   * @groupdesc some text
         |   * @groupprio some text
         |   * @constructor some text
         |   * @return some text
         |   * @tparam T some text
         |   * @throws Exception some text
         |   * @param p some text
         |   */
         |  def ${|}foo[T](p: String) = 42
         |}
         |""".stripMargin

    val expectedDoc =
      s"""$SectionsStart
         |<tr><td valign='top' class='section'><p>Deprecated</td>
         |<td valign='top'>some text</td>
         |<tr><td valign='top' class='section'><p>Params:</td>
         |<td valign='top'>p &ndash; some text</td>
         |<tr><td valign='top' class='section'><p>Type parameters:</td>
         |<td valign='top'>T &ndash; some text</td>
         |<tr><td valign='top' class='section'><p>Returns:</td>
         |<td valign='top'>some text</td>
         |<tr><td valign='top' class='section'><p>Throws:</td>
         |<td valign='top'><a href="psi_element://$exceptionClass"><code>Exception</code></a> &ndash; some text</td>
         |<tr><td valign='top' class='section'><p>Note:</td>
         |<td valign='top'>some text</td>
         |<tr><td valign='top' class='section'><p>Example:</td>
         |<td valign='top'>some text</td>
         |<tr><td valign='top' class='section'><p>See also:</td>
         |<td valign='top'>some text</td>
         |<tr><td valign='top' class='section'><p>Since:</td>
         |<td valign='top'>some text</td>
         |<tr><td valign='top' class='section'><p>Author:</td>
         |<td valign='top'>some text</td>
         |<tr><td valign='top' class='section'><p>Version:</td>
         |<td valign='top'>some text</td>
         |<tr><td valign='top' class='section'><p>Todo:</td>
         |<td valign='top'>some text</td>
         |$SectionsEnd
         |""".stripMargin
    doGenerateDocBodyTest(fileText, expectedDoc)
  }

  def testUnresolvedReference(): Unit =
    doGenerateDocBodyTest(
      s"""/**
         | * [[unknown.Reference]]
         | * [[unknown.Reference ref label]]
         | * @throws unknown.Reference description
         | */
         |def ${|}foo = ???""".stripMargin,
      s"""$ContentStart
         |<code>unknown.Reference</code>
         | <code>ref label</code>
         |$ContentEnd
         |$SectionsStart
         |<tr><td valign='top' class='section'><p>Throws:</td>
         |<td valign='top'><code>unknown.Reference</code> &ndash; description</td>
         |$SectionsEnd
         |""".stripMargin
    )
}
