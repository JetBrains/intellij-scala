package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.base.SharedTestProjectToken
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword.{DEF, OVERRIDE}
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings.{alwaysAddType, set}
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}

abstract class ScalaOverrideCompletionTestBase extends ScalaCompletionTestBase {

  override protected def sharedProjectToken: SharedTestProjectToken =
    SharedTestProjectToken.ByTestClassAndScalaSdkAndProjectLibraries(this)

  protected override def setUp(): Unit = {
    super.setUp()

    set(getProject, alwaysAddType(getScalaCodeStyleSettings))
  }

  protected def checkNoOverrideCompletion(fileText: String, lookupString: String): Unit =
    super.checkNoCompletion(fileText) { lookup =>
      lookup.getLookupString.contains(OVERRIDE) &&
        lookup.getAllLookupStrings.contains(lookupString)
    }

  protected def doCompletionTest(
    fileText: String,
    resultText: String,
    items: String*
  ): Unit = {
    super.doRawCompletionTest(fileText, resultText) { lookup =>
      val lookupString = lookup.getLookupString
      items.forall(lookupString.contains)
    }
  }

  protected def prepareFileText(fileText: String): String =
    s"""
       |trait Base {
       |  protected def foo(int: Int): Int = 45
       |  /**
       |    * text
       |    */
       |  type StringType = String
       |  val intValue = 45
       |  var intVariable: Int
       |  type A
       |  def abstractFoo
       |
       |  @throws(classOf[Exception])
       |  def annotFoo(int: Int): Int = 45
       |}
       |
       |${fileText.withNormalizedSeparator.trim}
    """.stripMargin

  override protected def configureFromFileText(fileText: String): PsiFile =
    super.configureFromFileText(prepareFileText(fileText))

  override protected def checkResultByText(expectedFileText: String, ignoreTrailingSpaces: Boolean): Unit =
    super.checkResultByText(prepareFileText(expectedFileText), ignoreTrailingSpaces)
}

