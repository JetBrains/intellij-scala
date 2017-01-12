package org.jetbrains.plugins.scala.javaHighlighting

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, Error, Message, ScalaAnnotator}
import org.jetbrains.plugins.scala.base.{AssertMatches, ScalaFixtureTestCase}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
  * @author Alefas
  * @since 23/03/16
  */
abstract class JavaHighlitghtingTestBase extends ScalaFixtureTestCase with AssertMatches {

  override protected val scalaSdkVersion: ScalaSdkVersion = ScalaSdkVersion._2_11

  private var filesCreated: Boolean = false

  def errorsFromJavaCode(scalaFileText: String, javaFileText: String, javaClassName: String): List[Message] = {
    if (filesCreated) throw new AssertionError("Don't add files 2 times in a single test")

    fixture.addFileToProject("dummy.scala", scalaFileText)
    val myFile: PsiFile = fixture.addFileToProject(javaClassName + JavaFileType.DOT_DEFAULT_EXTENSION, javaFileText)
    fixture.openFileInEditor(myFile.getVirtualFile)
    val allInfo = fixture.doHighlighting()

    filesCreated = true

    import scala.collection.JavaConverters._
    allInfo.asScala.toList.collect {
      case highlightInfo if highlightInfo.`type`.getSeverity(null) == HighlightSeverity.ERROR =>
        new Error(highlightInfo.getText, highlightInfo.getDescription)
    }
  }

  def errorsFromScalaCode(scalaFileText: String, javaFileText: String): List[Message] = {
    if (filesCreated) throw new AssertionError("Don't add files 2 times in a single test")

    fixture.addFileToProject("dummy.java", javaFileText)
    fixture.configureByText("dummy.scala", scalaFileText)

    filesCreated = true

    PsiDocumentManager.getInstance(getProject).commitAllDocuments()

    val mock = new AnnotatorHolderMock(getFile)
    val annotator = new ScalaAnnotator

    getFile.depthFirst().foreach(annotator.annotate(_, mock))
    mock.annotations.filter {
      case Error(_, null) | Error(null, _) => false
      case Error(_, _) => true
      case _ => false
    }
  }

  val CannotResolveMethod = ContainsPattern("Cannot resolve method")
  val CannotBeApplied = ContainsPattern("cannot be applied")
  val CannotBeInstantianted = ContainsPattern("is abstract; cannot be instantiated")

  case class ContainsPattern(fragment: String) {
    def unapply(s: String) = s.contains(fragment)
  }
}
