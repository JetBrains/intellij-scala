package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightTestBase
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.junit.Assert

/**
 * @author Alefas
 * @since 23.03.12
 */
class ScalaLookupRenderingTest extends ScalaCodeInsightTestBase {
  def testJavaVarargs() {
    val javaFileText =
      """
      |package a;
      |
      |public class Java {
      |  public static void foo(int... x) {}
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    val fileText =
      """
      |import a.Java
      |class A {
      |  Java.fo<caret>
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    inWriteAction {
      val myVFile = getSourceRootAdapter.createChildDirectory(null, "a").createChildData(null, "Java.java")
      VfsUtil.saveText(myVFile, javaFileText)
    }

    configureFromFileTextAdapter("dummy.scala", fileText)
    val lookups = complete(1, CompletionType.BASIC)

    val resultText =
      """
      |foo(x: Int*)
      """.stripMargin('|').replaceAll("\r", "").trim()

    val result = lookups.filter(_.getLookupString == "foo").map(p => {
      val presentation: LookupElementPresentation = new LookupElementPresentation
      p.renderElement(presentation)
      presentation.getItemText + presentation.getTailText
    }).sorted.mkString("\n")

    Assert.assertEquals(resultText, result)
  }
}
