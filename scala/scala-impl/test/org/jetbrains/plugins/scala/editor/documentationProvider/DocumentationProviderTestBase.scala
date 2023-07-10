package org.jetbrains.plugins.scala
package editor.documentationProvider

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.lang.documentation.psi.PsiElementDocumentationTarget
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.project.ProjectContext
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters.ListHasAsScala

@Category(Array(classOf[SlowTests]))
abstract class DocumentationProviderTestBase
  extends ScalaLightCodeInsightFixtureTestCase
    with DocumentationTesting {

  protected def documentationProvider: DocumentationProvider

  protected implicit def projectContext: ProjectContext = super.getProject

  override protected def generateDoc(editor: Editor, file: PsiFile): String = {
    val (referredElement, elementAtCaret) = extractReferredAndOriginalElements(editor, file)
    generateDoc(referredElement, elementAtCaret)
  }

  protected def generateDoc(referredElement: PsiElement, elementAtCaret: PsiElement): String =
    documentationProvider.generateDoc(referredElement, elementAtCaret)

  override final protected def createEditorAndFile(fileContent: String): (Editor, PsiFile) = {
    val file = createFile(fileContent)
    (myFixture.getEditor, file)
  }

  protected def createFile(fileContent: String): PsiFile

  /** see parameters of [[com.intellij.lang.documentation.DocumentationProvider#generateDoc]] */
  //noinspection UnstableApiUsage
  protected def extractReferredAndOriginalElements(editor: Editor, file: PsiFile): (PsiElement, PsiElement) = {
    val docTargetProvider = IdeDocumentationTargetProvider.getInstance(file.getProject)

    //This code is the closest to how IntelliJ platform calculates target element for which documentation should be generated
    //Unfortunately looks like there is no single test API,
    //which would encapsulate the entire chain between "Quick Doc" action invocation and documentation provider invocation
    val docTargets = docTargetProvider.documentationTargets(editor, file, editor.getCaretModel.getOffset).asScala.toSeq

    val elementAtCaret = file.findElementAt(editor.getCaretModel.getOffset)
    assertEquals(s"Expecting single target. Actual targets:\n${docTargets.mkString("\n")}", 1, docTargets.size)

    docTargets.head match {
      case target: PsiElementDocumentationTarget =>
        (target.getTargetElement, elementAtCaret)
      case other =>
        Assert.fail(s"Unexpected element targets $other").asInstanceOf[Nothing]
    }
  }
}
