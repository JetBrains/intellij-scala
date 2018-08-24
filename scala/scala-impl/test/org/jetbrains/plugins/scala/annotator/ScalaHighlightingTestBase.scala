package org.jetbrains.plugins.scala.annotator

import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.base.{AssertMatches, ScalaFixtureTestCase}
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_11}
import org.jetbrains.plugins.scala.extensions.PsiElementExt

/**
  * @author Alefas
  * @since 23/03/16
  */
abstract class ScalaHighlightingTestBase extends ScalaFixtureTestCase with AssertMatches {

  private var filesCreated: Boolean = false

  override implicit val version: ScalaVersion = Scala_2_11

  def errorsFromScalaCode(scalaFileText: String): List[Message] = {
    if (filesCreated) throw new AssertionError("Don't add files 2 times in a single test")

    myFixture.configureByText("dummy.scala", scalaFileText)

    filesCreated = true

    PsiDocumentManager.getInstance(getProject).commitAllDocuments()

    val mock = new AnnotatorHolderMock(getFile)
    val annotator = ScalaAnnotator.forProject

    getFile.depthFirst().foreach(annotator.annotate(_, mock))
    mock.annotations.filter {
      case Error(_, null) | Error(null, _) => false
      case Error(_, _) => true
      case _ => false
    }
  }
}
