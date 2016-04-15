package org.jetbrains.plugins.scala.javaHighlighting

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, Error, Message, ScalaAnnotator}
import org.jetbrains.plugins.scala.base.{AssertMatches, ScalaFixtureTestCase, ScalaLibraryLoader}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.util.TestUtils

/**
  * @author Alefas
  * @since 23/03/16
  */
abstract class JavaHighlitghtingTestBase extends ScalaFixtureTestCase with AssertMatches {
  def errorsFromJavaCode(scalaFileText: String, javaFileText: String, javaClassName: String): List[Message] = {
    myFixture.addFileToProject("dummy.scala", scalaFileText)
    val myFile: PsiFile = myFixture.addFileToProject(javaClassName + JavaFileType.DOT_DEFAULT_EXTENSION, javaFileText)
    myFixture.openFileInEditor(myFile.getVirtualFile)
    val allInfo = myFixture.doHighlighting()

    import scala.collection.JavaConverters._
    allInfo.asScala.toList.collect {
      case highlightInfo if highlightInfo.`type`.getSeverity(null) == HighlightSeverity.ERROR =>
        new Error(highlightInfo.getText, highlightInfo.getDescription)
    }
  }

  def erorrsFromScalaCode(scalaFileText: String, javaFileText: String): List[Message] = {
    myFixture.addFileToProject("dummy.java", javaFileText)
    myFixture.configureByText("dummy.scala", scalaFileText)
    PsiDocumentManager.getInstance(getProject).commitAllDocuments()

    val mock = new AnnotatorHolderMock
    val annotator = new ScalaAnnotator

    getFile.depthFirst.foreach(annotator.annotate(_, mock))
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

  private var scalaLibraryLoader: ScalaLibraryLoader = null

  override def setUp() = {
    super.setUp()

    TestUtils.setLanguageLevel(getProject, LanguageLevel.JDK_1_8)
    scalaLibraryLoader = new ScalaLibraryLoader(getProject, myFixture.getModule, null)
    scalaLibraryLoader.loadScala(TestUtils.DEFAULT_SCALA_SDK_VERSION)
  }

  override def tearDown(): Unit = {
    scalaLibraryLoader.clean()
    super.tearDown()
  }
}
