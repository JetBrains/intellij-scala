package org.jetbrains.plugins.scala
package base

import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.{PsiComment, PsiElement, PsiFileFactory, PsiWhiteSpace}
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures._
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.reflect.ClassTag

/**
 * Pavel.Fatin, 18.05.2010
 */

abstract class SimpleTestCase extends UsefulTestCase with AssertMatches {
  var fixture: CodeInsightTestFixture = null

  implicit def ctx: ProjectContext = fixture.getProject

  override def setUp(): Unit = {
    super.setUp()
    val fixtureBuilder: TestFixtureBuilder[IdeaProjectTestFixture] =
      IdeaTestFixtureFactory.getFixtureFactory.createFixtureBuilder("SimpleTestCase")

    fixture = IdeaTestFixtureFactory.getFixtureFactory.createCodeInsightFixture(fixtureBuilder.getFixture)
    fixture.setUp()
  }

  override def tearDown(): Unit = try {
    fixture.tearDown()
  } finally {
    fixture = null
    super.tearDown()
  }

  def parseText(@Language("Scala") s: String, enableEventSystem: Boolean = false): ScalaFile = {
    PsiFileFactory.getInstance(fixture.getProject)
      .createFileFromText("foo.scala", ScalaFileType.INSTANCE, s, System.currentTimeMillis(), enableEventSystem)
      .asInstanceOf[ScalaFile]
  }

  def parseText(@Language("Scala") s: String, caretMarker: String): (ScalaFile, Int) = {
    val trimmed = s.trim
    val caretPos = trimmed.indexOf(caretMarker)
    (parseText(trimmed.replaceAll(caretMarker, "")), caretPos)
  }

  implicit class Findable(val element: ScalaFile) {
    def target: PsiElement = element.depthFirst()
      .dropWhile(!_.isInstanceOf[PsiComment])
      .drop(1)
      .dropWhile(_.isInstanceOf[PsiWhiteSpace])
      .next()
  }

  def describe(tree: PsiElement): String = toString(tree, 0)
  
  private def toString(root: PsiElement, level: Int): String = {
    val indent = List.fill(level)("  ").mkString
    val content = if (root.isInstanceOf[LeafPsiElement])
      "\"%s\"".format(root.getText) else root.getClass.getSimpleName
    val title = "%s%s\n".format(indent, content)
    title + root.children.map(toString(_, level + 1)).mkString
  }
  
  implicit class ScalaCode(@Language("Scala") val s: String) {
    def stripComments: String =
      s.replaceAll("""(?s)/\*.*?\*/""", "")
              .replaceAll("""(?m)//.*$""", "")

    def parse: ScalaFile = parseText(s)

    def parseWithEventSystem: ScalaFile = parseText(s, enableEventSystem = true)

    def parse[T <: PsiElement: ClassTag]: T =
      parse.depthFirst().findByType[T].getOrElse {
        throw new RuntimeException("Unable to find PSI element with type " +
          implicitly[ClassTag[T]].runtimeClass.getSimpleName)
      }
  }

  case class ContainsPattern(fragment: String) {
    def unapply(s: String) = s.contains(fragment)
  }
}