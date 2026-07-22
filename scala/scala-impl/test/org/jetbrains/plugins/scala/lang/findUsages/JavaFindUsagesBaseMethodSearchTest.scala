package org.jetbrains.plugins.scala.lang.findUsages

import com.intellij.find.findUsages.JavaFindUsagesHandlerFactory
import com.intellij.psi.util.{PsiFormatUtil, PsiFormatUtilBase, PsiTreeUtil}
import com.intellij.psi.{PsiMethod, PsiSubstitutor}
import com.intellij.testFramework.EdtTestUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.Assert.assertEquals

class JavaFindUsagesBaseMethodSearchTest extends ScalaLightCodeInsightFixtureTestCase {

  def testBaseMethodSearchDoesNotTreatIndirectTraitOverrideAsAnIndependentRoot(): Unit =
    assertBaseMethodSearchRoots(
      //language=Scala
      """trait ScalaTrait1 extends JavaBase {
        |  override def foo = 1
        |}
        |
        |trait ScalaTrait2 extends ScalaTrait1
        |
        |class ScalaClass extends ScalaTrait2 {
        |  override def foo = 2
        |}
        |""".stripMargin,
      //language=JAVA
      s"""public class JavaBase {
         |    public int ${CARET}foo() {
         |        return 0;
         |    }
         |}
         |""".stripMargin,
      //TODO: fix the expected data once IDEA-391962 is fixed
      Seq("ScalaTrait1.foo()", "JavaBase.foo()")
      //Seq("JavaBase.foo()")
    )

  def testBaseMethodSearchHandlesIndirectAbstractClassOverride(): Unit =
    assertBaseMethodSearchRoots(
      //language=Scala
      """abstract class ScalaAbstractClass1 extends JavaBase {
        |  override def foo = 1
        |}
        |
        |abstract class ScalaAbstractClass2 extends ScalaAbstractClass1
        |
        |class ScalaClass extends ScalaAbstractClass2 {
        |  override def foo = 2
        |}
        |""".stripMargin,
      //language=JAVA
      s"""public class JavaBase {
         |    public int ${CARET}foo() {
         |        return 0;
         |    }
         |}
         |""".stripMargin,
      Seq("JavaBase.foo()")
    )

  /**
   * Asserts the method roots that Java Find Usages searches when its "Search for base method" option is enabled.
   *
   * [[com.intellij.find.findUsages.JavaFindUsagesHandler#getPrimaryElements]] delegates to
   * `SuperMethodWarningUtil.getTargetMethodCandidates` for a method. That helper has a fallback for methods
   * inherited through an independent interface branch. The indirect trait hierarchy in this test must not satisfy
   * that fallback: `ScalaTrait1.foo` overrides `JavaBase.foo`; it is not an independent method root.
   *
   * This deliberately tests Find Usages rather than Rename. Rename has a separate entry point and is covered by
   * `ScalaRenameTest`.
   */
  private def assertBaseMethodSearchRoots(
    scalaSource: String,
    javaSource: String,
    expectedPresentations: Seq[String]
  ): Unit = {
    myFixture.addFileToProject(
      "ScalaDefinitions.scala",
      scalaSource
    )
    myFixture.configureByText(
      "JavaBase.java",
      javaSource
    )

    val baseMethod = PsiTreeUtil.getParentOfType(myFixture.getElementAtCaret, classOf[PsiMethod], false)
    val factory = JavaFindUsagesHandlerFactory.getInstance(getProject)
    val options = factory.getFindMethodOptions
    val oldSearchForBaseMethod = options.isSearchForBaseMethod
    options.isSearchForBaseMethod = true

    val candidatePresentations = try {
      val handler = factory.createFindUsagesHandler(baseMethod, false)
      EdtTestUtil.runInEdtAndGet(() =>
        handler.getPrimaryElements.toSeq.collect { case method: PsiMethod => presentMethod(method) }
      )
    } finally {
      options.isSearchForBaseMethod = oldSearchForBaseMethod
    }

    assertEquals(expectedPresentations, candidatePresentations)
  }

  private def presentMethod(method: PsiMethod): String = PsiFormatUtil.formatMethod(
    method,
    PsiSubstitutor.EMPTY,
    PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
    0
  )
}
