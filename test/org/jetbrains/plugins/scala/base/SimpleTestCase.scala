package org.jetbrains.plugins.scala
package base

import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.testFramework.fixtures.{IdeaTestFixtureFactory}
import org.jetbrains.plugins.scala.{ScalaFileType}
import com.intellij.psi.{PsiElement, PsiWhiteSpace, PsiComment, PsiFileFactory}
import junit.framework.{TestCase, Assert}
import org.intellij.lang.annotations.Language

/**
 * Pavel.Fatin, 18.05.2010
 */

abstract class SimpleTestCase extends TestCase {
  val fixture = IdeaTestFixtureFactory.getFixtureFactory.createFixtureBuilder.getFixture

  override def setUp() = fixture.setUp

  override def tearDown() = fixture.tearDown

  def parseText(@Language("Scala") s: String): ScalaFile = {
    PsiFileFactory.getInstance(fixture.getProject)
            .createFileFromText("foo" + ScalaFileType.DEFAULT_EXTENSION, ScalaFileType.SCALA_FILE_TYPE, s)
            .asInstanceOf[ScalaFile]
  }

  implicit def toParseable(@Language("Scala") s: String) = new {
    def parse: ScalaFile = parseText(s)
  
    def parse[T <: PsiElement](aClass: Class[T]): T = 
      parse.depthFirst.findByType(aClass).getOrElse {
        throw new RuntimeException("Unable to find PSI element with type " + aClass.getSimpleName)
      }
  }

  implicit def toFindable(element: ScalaFile) = new {
    def target: PsiElement = element.depthFirst
            .dropWhile(!_.isInstanceOf[PsiComment])
            .drop(1)
            .dropWhile(_.isInstanceOf[PsiWhiteSpace])
            .next
  }
  
  def assertMatches[T](actual: T)(pattern: PartialFunction[T, Unit]) {
    Assert.assertTrue("actual: " + actual.toString, pattern.isDefinedAt(actual))
  }
}