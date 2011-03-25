package org.jetbrains.plugins.scala
package base

import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.{ScalaFileType}
import com.intellij.psi.{PsiElement, PsiWhiteSpace, PsiComment, PsiFileFactory}
import junit.framework.{TestCase, Assert}
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.extensions._
import util.ScalaUtils
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder
import com.intellij.util.PathUtil
import com.intellij.testFramework.fixtures._
import com.intellij.testFramework.UsefulTestCase

/**
 * Pavel.Fatin, 18.05.2010
 */

abstract class SimpleTestCase extends UsefulTestCase {
  var fixture: CodeInsightTestFixture = null

  override def setUp() {
    super.setUp()
    val fixtureBuilder: TestFixtureBuilder[IdeaProjectTestFixture] =
      IdeaTestFixtureFactory.getFixtureFactory.createFixtureBuilder

    fixture = IdeaTestFixtureFactory.getFixtureFactory.createCodeInsightFixture(fixtureBuilder.getFixture)
    fixture.setUp()
  }

  override def tearDown() {
    fixture.tearDown()
    fixture = null
    super.tearDown()
  }

  def parseText(@Language("Scala") s: String): ScalaFile = {
    PsiFileFactory.getInstance(fixture.getProject)
            .createFileFromText("foo" + ScalaFileType.DEFAULT_EXTENSION, ScalaFileType.SCALA_FILE_TYPE, s)
            .asInstanceOf[ScalaFile]
  }

  implicit def toCode(@Language("Scala") s: String) = new ScalaCode(s)

  implicit def toFindable(element: ScalaFile) = new {
    def target: PsiElement = element.depthFirst
      .dropWhile(!_.isInstanceOf[PsiComment])
      .drop(1)
      .dropWhile(_.isInstanceOf[PsiWhiteSpace])
      .next()
  }

  def assertNothing[T](actual: T) {
    assertMatches(actual) {
      case Nil =>
    }
  }

  def assertMatches[T](actual: T)(pattern: PartialFunction[T, Unit]) {
    Assert.assertTrue("actual: " + actual.toString, pattern.isDefinedAt(actual))
  }

  class ScalaCode(@Language("Scala") s: String) {
    def stripComments: String =
      s.replaceAll("""(?s)/\*.*?\*/""", "")
              .replaceAll("""(?m)//.*$""", "")

    def parse: ScalaFile = parseText(s)

    def parse[T <: PsiElement](aClass: Class[T]): T =
      parse.depthFirst.findByType(aClass).getOrElse {
        throw new RuntimeException("Unable to find PSI element with type " + aClass.getSimpleName)
      }
  }
}