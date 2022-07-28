package org.jetbrains.plugins.scala
package javaHighlighting

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, Error, Message, ScalaAnnotator}
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.util.assertions.MatcherAssertions
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
abstract class JavaHighlightingTestBase extends ScalaFixtureTestCase with MatcherAssertions {
  override protected def supportedIn(version: ScalaVersion): Boolean = version  >= LatestScalaVersions.Scala_2_11

  private var filesCreated: Boolean = false

  def errorsFromJavaCode(scalaFileText: String, javaFileText: String, javaClassName: String): List[Message] = {
    if (filesCreated) throw new AssertionError("Don't add files 2 times in a single test")

    myFixture.addFileToProject("dummy.scala", scalaFileText)
    val myFile: PsiFile = myFixture.addFileToProject(javaClassName + JavaFileType.DOT_DEFAULT_EXTENSION, javaFileText)
    myFixture.openFileInEditor(myFile.getVirtualFile)
    val allInfo = myFixture.doHighlighting()

    filesCreated = true

    import scala.jdk.CollectionConverters._
    allInfo.asScala.toList.collect {
      case highlightInfo if highlightInfo.`type`.getSeverity(null) == HighlightSeverity.ERROR =>
        Error(highlightInfo.getText, highlightInfo.getDescription)
    }
  }

  def errorsFromScalaCode(scalaFileText: String, javaFileText: String): List[Message] = {
    if (filesCreated) throw new AssertionError("Don't add files 2 times in a single test")

    myFixture.addFileToProject("dummy.java", javaFileText)
    myFixture.configureByText("dummy.scala", scalaFileText)

    filesCreated = true

    PsiDocumentManager.getInstance(getProject).commitAllDocuments()

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(getFile)
    val annotator = ScalaAnnotator.forProject

    getFile.depthFirst().foreach(annotator.annotate)
    mock.annotations.filter {
      case Error(_, null) | Error(null, _) => false
      case Error(_, _) => true
      case _ => false
    }
  }
}
