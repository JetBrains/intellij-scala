package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingTestBase
import org.jetbrains.plugins.scala.codeInspection.{ScalaAnnotatorQuickFixTestBase, ScalaInspectionBundle, ScalaQuickFixTestFixture}
import org.jetbrains.plugins.scala.extensions.inWriteCommandAction

import scala.jdk.CollectionConverters.ListHasAsScala

/**
 * Also see a related inspection tests [[org.jetbrains.plugins.scala.codeInspection.methodSignature.ParameterlessAccessInspectionEmptyParenMethodTest]]
 */
abstract class ScReferenceAnnotatorTestBase extends ScalaHighlightingTestBase

class ScReferenceAnnotatorTestBase_Scala2 extends ScReferenceAnnotatorTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  def testMethodWithoutArgumentsAccessedAsReference(): Unit = {
    // In Scala 2.13 it's a warning, shown in inspection
    // See:
    //  - org.jetbrains.plugins.scala.codeInspection.methodSignature.ParameterlessAccessInspection.EmptyParenMethod
    //  - org.jetbrains.plugins.scala.codeInspection.methodSignature.ParameterlessInspectionTest
    assertNoErrors(
      """def foo1: String = null
        |def foo2(): String = null
        |def foo3()(implicit x: String): String = null
        |
        |implicit val s: String = null
        |
        |foo1
        |foo2
        |foo3
        |""".stripMargin
    )
  }
}

class ScReferenceAnnotatorTestBase_Scala3 extends ScReferenceAnnotatorTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3

  def testMethodWithoutArgumentsAccessedAsReference(): Unit = {
    // In Scala 2.13 it's a warning, shown in inspection
    // See:
    //  - org.jetbrains.plugins.scala.codeInspection.methodSignature.ParameterlessAccessInspection.EmptyParenMethod
    //  - org.jetbrains.plugins.scala.codeInspection.methodSignature.ParameterlessInspectionTest
    assertErrorsText(
      """def foo1: String = null
        |def foo2(): String = null
        |def foo3()(implicit x: String): String = null
        |
        |implicit val s: String = null
        |
        |foo1
        |foo2
        |foo3
        |""".stripMargin,
      """Error(foo2,Method foo2 must be called with () argument)
        |Error(foo3,Method foo3 must be called with () argument)
        |""".stripMargin
    )

    applyAllQuickFixesWithText(ScalaInspectionBundle.message("add.call.parentheses"))

    myFixture.checkResult(
      """def foo1: String = null
        |def foo2(): String = null
        |def foo3()(implicit x: String): String = null
        |
        |implicit val s: String = null
        |
        |foo1
        |foo2()
        |foo3()
        |""".stripMargin
    )
  }

  private def applyAllQuickFixesWithText(fixText: String): Unit = {
    val highlights = myFixture.doHighlighting().asScala.toSeq
    val fixesAll = highlights.flatMap(ScalaQuickFixTestFixture.findRegisteredQuickFixes)
    val fixes = fixesAll.filter(_.getText == fixText)
    inWriteCommandAction {
      fixes.foreach(_.invoke(getProject, getEditor, getFile))
    }
  }
}