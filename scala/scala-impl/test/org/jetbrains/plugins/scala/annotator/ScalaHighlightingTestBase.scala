package org.jetbrains.plugins.scala.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.TypecheckerTests
import org.jetbrains.plugins.scala.annotator.hints.AnnotatorHints
import org.jetbrains.plugins.scala.base.{AssertMatches, ScalaFixtureTestCase}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.junit.experimental.categories.Category

/**
  * @author Alefas
  * @since 23/03/16
  */
@Category(Array(classOf[TypecheckerTests]))
abstract class ScalaHighlightingTestBase extends ScalaFixtureTestCase with AssertMatches {

  private var filesCreated: Boolean = false

  protected def withHints = false

  def errorsFromScalaCode(scalaFileText: String): List[Message] = {
    if (filesCreated) throw new AssertionError("Don't add files 2 times in a single test")

    myFixture.configureByText("dummy.scala", scalaFileText)

    filesCreated = true

    PsiDocumentManager.getInstance(getProject).commitAllDocuments()

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(getFile)

    getFile.depthFirst().foreach(annotate(_))

    val messages = mock.annotations.filter {
      case Error(_, null) | Error(null, _) => false
      case Error(_, _) => true
      case _ => false
    }

    if (withHints) {
      // TODO allow to check prefix / suffix, text attributes, error tooltip
      val hints = getFile.elements
        .flatMap(AnnotatorHints.in(_).toSeq.flatMap(_.hints))
        .map(hint => Hint(hint.element.getText, hint.parts.map(_.string).mkString, offsetDelta = hint.offsetDelta)).toList
      hints ::: messages
    } else {
      messages
    }
  }

  def annotate(element: PsiElement)
              (implicit holder: AnnotationHolder): Unit =
    ScalaAnnotator.forProject.annotate(element, holder)
}
