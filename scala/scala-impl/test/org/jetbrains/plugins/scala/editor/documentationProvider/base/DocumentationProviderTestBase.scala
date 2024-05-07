package org.jetbrains.plugins.scala.editor.documentationProvider.base

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.lang.documentation.psi.PsiElementDocumentationTarget
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestCase, SharedTestProjectToken}
import org.jetbrains.plugins.scala.editor.documentationProvider.util.{HtmlAssertions, ScalaDocumentationsSectionsTestingBase}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScDocCommentOwner
import org.jetbrains.plugins.scala.project.ProjectContext
import org.junit.Assert
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters.ListHasAsScala

@Category(Array(classOf[SlowTests]))
abstract class DocumentationProviderTestBase
  extends ScalaLightCodeInsightFixtureTestCase
    with HtmlAssertions {

  protected val | : String = EditorTestUtil.CARET_TAG

  protected implicit def projectContext: ProjectContext = super.getProject

  override protected def sharedProjectToken: SharedTestProjectToken =
    SharedTestProjectToken.ByTestClassAndScalaSdkAndProjectLibraries(this)

  /////////////////// section start ////////////////////////
  protected def documentationProvider: DocumentationProvider

  protected final def generateDoc(referredElement: PsiElement, elementAtCaret: PsiElement): String =
    documentationProvider.generateDoc(referredElement, elementAtCaret)

  protected final def generateQuickNavigateInfo(referredElement: PsiElement, elementAtCaret: PsiElement): String =
    documentationProvider.getQuickNavigateInfo(referredElement, elementAtCaret)

  protected final def generateRenderedDoc(referredElement: PsiElement): String =
    documentationProvider.generateRenderedDoc(referredElement.asInstanceOf[ScDocCommentOwner].getDocComment)
  /////////////////// section end ////////////////////////

  /////////////////// section start ////////////////////////
  protected final def configureFileAndGenerateDoc(fileContent: String): String = {
    val (editor, file) = createEditorAndFile(fileContent)
    assertTrue("file should contain valid psi tree", file.isValid)

    generateDoc(editor, file)
  }

  private def generateDoc(editor: Editor, file: PsiFile): String = {
    val (referredElement, elementAtCaret) = extractReferredAndOriginalElements(editor, file)
    generateDoc(referredElement, elementAtCaret)
  }

  protected final def generateQuickNavigateInfo(fileContent: String): String = {
    val (editor, file) = createEditorAndFile(fileContent)
    assertTrue("file should contain valid psi tree", file.isValid)

    val (referredElement, elementAtCaret) = extractReferredAndOriginalElements(editor, file)
    generateQuickNavigateInfo(referredElement, elementAtCaret)
  }

  protected final def generateRenderedDoc(fileContent: String): String = {
    val (editor, file) = createEditorAndFile(fileContent)
    assertTrue("file should contain valid psi tree", file.isValid)

    val (referredElement, _) = extractReferredAndOriginalElements(editor, file)
    generateRenderedDoc(referredElement)
  }

  private def createEditorAndFile(fileContent: String): (Editor, PsiFile) = {
    configureFixtureFromText(fileContent)
    (myFixture.getEditor, getFixture.getFile)
  }

  protected def configureFixtureFromText(fileContent: String): Unit

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
  /////////////////// section end ////////////////////////

  /////////////////// section start ////////////////////////
  protected final def doGenerateDocTest(
    fileContent: String,
    expectedDoc: => String,
    whitespacesMode: HtmlSpacesComparisonMode = HtmlSpacesComparisonMode.IgnoreNewLinesAndCollapseSpaces
  ): Unit = {
    val actualDoc = configureFileAndGenerateDoc(fileContent)
    assertDocHtml(expectedDoc, actualDoc, whitespacesMode)
  }

  /** NOTE: test fixture should be setup in advance */
  protected final def doGenerateDocAtCaretTest(
    expectedDoc: => String,
    whitespacesMode: HtmlSpacesComparisonMode = HtmlSpacesComparisonMode.IgnoreNewLinesAndCollapseSpaces
  ): Unit = {
    val actualDoc = generateDoc(getEditor, getFile)
    assertDocHtml(expectedDoc, actualDoc, whitespacesMode)
  }

  protected final def doGenerateRenderedDocTest(
    fileContent: String,
    expectedDoc: => String,
    whitespacesMode: HtmlSpacesComparisonMode = HtmlSpacesComparisonMode.IgnoreNewLinesAndCollapseSpaces
  ): Unit = {
    val actualDoc = generateRenderedDoc(fileContent)
    assertDocHtml(expectedDoc, actualDoc, whitespacesMode)
  }
  /////////////////// section end ////////////////////////
}
