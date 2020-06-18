package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

class ScalaDocumentationFromJavaTest extends DocumentationProviderTestBase
  with ScalaDocumentationsSectionsTesting {

  //noinspection NotImplementedCode
  //will be autodetected by platform when calling doc from java file
  override protected def documentationProvider: DocumentationProvider = ???

  override protected def generateDoc(editor: Editor, file: PsiFile) = {
    val manager = DocumentationManager.getInstance(getProject)
    val target  = manager.findTargetElement(getEditor, getFile)
    manager.generateDocumentation(target, null, false)
  }

  override protected def createFile(fileContent: String): PsiFile =
    getFixture.configureByText(JavaFileType.INSTANCE, fileContent)

  def testReferenceToMethodInScalaObject_SCL_8760(): Unit = {
    getFixture.addFileToProject("ScalaObject.scala",
      s"""object ScalaObject {
         |  /**
         |   * Some description
         |   * @param s some parameter
         |   * @param t another parameter (indented)
         |   */
         |  def scalaMethod(s: String, t: String): Unit = ???
         |}""".stripMargin
    )

    doGenerateDocTest(
      s"""class JavaClass {
         |    public static void main(String[] args) {
         |        ScalaObject.${|}scalaMethod("dummy1", "dummy1");
         |    }
         |}""".stripMargin,
      s"""$DocStart
         |$DefinitionStart<a href="psi_element://ScalaObject"><code>ScalaObject</code></a>
         |def <b>scalaMethod</b>(s: <a href="psi_element://scala.Predef.String"><code>String</code></a>, t: <a href="psi_element://scala.Predef.String"><code>String</code></a>): <a href="psi_element://scala.Unit"><code>Unit</code></a>$DefinitionEnd
         |$ContentStart
         |<p>Some description
         |$ContentEnd
         |$SectionsStart
         |<tr><td valign='top' class='section'><p>Params:</td>
         |<td valign='top'>s &ndash; some parameter  <p>t &ndash; another parameter (indented)</td>
         |$SectionsEnd
         |$DocEnd
         |""".stripMargin
    )
  }
}