package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class DocumentationFromJavaTest extends ScalaLightCodeInsightFixtureTestAdapter
  with DocumentationTestLike {

  override protected def generateDoc(editor: Editor, file: PsiFile) = {
    val manager = DocumentationManager.getInstance(getProject)
    val target  = manager.findTargetElement(getEditor, getFile)
    manager.generateDocumentation(target, null, false)
  }

  override protected def createEditorAndFile(fileContent: String): (Editor, PsiFile) = {
    val file = getFixture.configureByText(JavaFileType.INSTANCE, fileContent)
    val editor = getFixture.getEditor
    (editor, file)
  }

  def testReferenceToMethodInScalaObject_SCL_8760(): Unit = {
    getFixture.addFileToProject("ScalaObject.scala",
      s"""object ScalaObject {
         |  /**
         |   * Some description
         |   * @param s some parameter
         |   */
         |  def scalaMethod(s: String): Unit = ???
         |}""".stripMargin
    )

    doShortGenerateDocTest(
      s"""class JavaClass {
         |    public static void main(String[] args) {
         |        ScalaObject.${|}scalaMethod("dummy");
         |    }
         |}""".stripMargin,
      """<div class="definition">
        |<a href="psi_element://ScalaObject"><code>ScalaObject</code></a>
        |<pre>
        |def <b>scalaMethod</b>(s: <a href="psi_element://scala.Predef.String"><code>String</code></a>)
        |: <a href="psi_element://scala.Unit"><code>Unit</code></a>
        |</pre>
        |</div>
        |<div class='content'>     Some description     <p></div>
        |<table class='sections'>
        |<p><tr>
        |<td valign='top' class='section'><p>Params:</td>
        |<td valign='top'>s &ndash; some parameter</td>
        |</table>""".stripMargin
    )
  }
}