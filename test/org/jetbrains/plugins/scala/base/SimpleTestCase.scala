package org.jetbrains.plugins.scala
package base

import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.ScalaFileType
import com.intellij.psi.{PsiElement, PsiWhiteSpace, PsiComment, PsiFileFactory}
import junit.framework.Assert
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.extensions._
import com.intellij.testFramework.fixtures._
import com.intellij.testFramework.UsefulTestCase
import com.intellij.psi.impl.source.tree.LeafPsiElement

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

  def describe(tree: PsiElement): String = toString(tree, 0)
  
  private def toString(root: PsiElement, level: Int): String = {
    val indent = List.fill(level)("  ").mkString
    val content = if (root.isInstanceOf[LeafPsiElement])
      "\"%s\"".format(root.getText) else root.getClass.getSimpleName
    val title = "%s%s\n".format(indent, content)
    title + root.children.map(toString(_, level + 1)).mkString
  }
  
  class ScalaCode(@Language("Scala") s: String) {
    def stripComments: String =
      s.replaceAll("""(?s)/\*.*?\*/""", "")
              .replaceAll("""(?m)//.*$""", "")

    def parse: ScalaFile = parseText(s)

    def parse[T <: PsiElement: ClassManifest]: T =
      parse(classManifest[T].erasure.asInstanceOf[Class[T]])

    def parse[T <: PsiElement](aClass: Class[T]): T =
      parse.depthFirst.findByType(aClass).getOrElse {
        throw new RuntimeException("Unable to find PSI element with type " + aClass.getSimpleName)
      }
  }
}