package org.jetbrains.plugins.scala
package base

import com.intellij.openapi.application.impl.TestOnlyThreading
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.{PsiComment, PsiElement, PsiWhiteSpace}
import com.intellij.testFramework.fixtures._
import com.intellij.testFramework.{LightProjectDescriptor, PlatformTestUtil, UsefulTestCase}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.assertions.MatcherAssertionsExt

abstract class SimpleTestCase extends UsefulTestCase with MatcherAssertionsExt with ScalaCodeParsing {

  var fixture: CodeInsightTestFixture = scala.compiletime.uninitialized

  implicit def ctx: ProjectContext = fixture.getProject

  override def setUp(): Unit = {
    super.setUp()
    fixture = createFixture()
    fixture.setUp()
    waitForSyntheticClasses()
  }

  /**
   * The post-startup activity can enqueue registration after the fixture has finished waiting for indexing.
   *
   * A production fix would register synthetic classes synchronously in
   * [[com.intellij.openapi.startup.StartupActivity.RequiredForSmartMode]], using the
   * `requiredForSmartModeStartupActivity` extension point, guaranteeing registration before smart mode.
   * We use this test-only wait because earlier experiments found that approach slowed project startup;
   * see [[org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.RegisterSyntheticClassesProjectActivity]] for the measurements.
   *
   * This fixes sporadic test failures
   */
  private def waitForSyntheticClasses(): Unit = {
    val syntheticClasses = SyntheticClasses.get(fixture.getProject)
    if (!syntheticClasses.isClassesRegistered) {
      invokeAndWait {
        TestOnlyThreading.releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack(() =>
          PlatformTestUtil.waitWithEventsDispatching(
            "Scala synthetic classes were not registered",
            () => syntheticClasses.isClassesRegistered,
            30
          )
        )
      }
    }
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
