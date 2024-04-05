package org.jetbrains.plugins.scala
package base

import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.{PsiComment, PsiElement, PsiWhiteSpace}
import com.intellij.testFramework.fixtures._
import com.intellij.testFramework.{LightProjectDescriptor, UsefulTestCase}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.assertions.MatcherAssertions

abstract class SimpleTestCase extends UsefulTestCase with MatcherAssertions with ScalaCodeParsing {

  var fixture: CodeInsightTestFixture = _

  implicit def ctx: ProjectContext = fixture.getProject

  override def setUp(): Unit = {
    super.setUp()
    fixture = createFixture()
    fixture.setUp()
  }

  protected def createFixture(): CodeInsightTestFixture = {
    val factory = IdeaTestFixtureFactory.getFixtureFactory
    val builder = factory.createLightFixtureBuilder(getProjectDescriptor, getTestName(false))
    factory.createCodeInsightFixture(builder.getFixture)
  }

  protected final def getProjectDescriptor: LightProjectDescriptor =
    new ScalaLightProjectDescriptor(sharedProjectToken)

  protected def sharedProjectToken: SharedTestProjectToken = SharedTestProjectToken(this.getClass)

  override def tearDown(): Unit = try {
    fixture.tearDown()
  } finally {
    fixture = null
    super.tearDown()
  }

  implicit class Findable(private val element: ScalaFile) {
    def target: PsiElement = element.depthFirst()
      .dropWhile(!_.is[PsiComment])
      .drop(1)
      .dropWhile(_.is[PsiWhiteSpace])
      .next()
  }

  def describe(tree: PsiElement): String = toString(tree, 0)

  private def toString(root: PsiElement, level: Int): String = {
    val indent = List.fill(level)("  ").mkString
    val content = if (root.is[LeafPsiElement])
      "\"%s\"".format(root.getText) else root.getClass.getSimpleName
    val title = "%s%s\n".format(indent, content)
    title + root.children.map(toString(_, level + 1)).mkString
  }
}
