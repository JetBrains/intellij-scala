package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.scala.lang.completion3.ScalaCodeInsightTestBase

//NOTE: if the class becomes big feel free to split it into more meaningful entities
class ScalaCompletionCodeTest extends ScalaCodeInsightTestBase {

  private val ScalaFileTextInSamePackage =
    s"""package org.example
       |
       |class MyScalaClassPublic {}
       |
       |private class MyScalaClassPackagePrivate {}
       |""".stripMargin

  private val JavaFileTextInSamePackage =
    """package org.example;
      |
      |public class MyJavaClassPublic {}
      |
      |class MyJavaClassPackagePrivate {}
      |""".stripMargin

  def testCompleteAfterNew_ShouldContainDefinitionsFromSamePackage_BothScalaAndJava_FromPackageObject(): Unit = {
    myFixture.addFileToProject("org/example/MyJavaClassPublic.java", JavaFileTextInSamePackage)
    myFixture.addFileToProject("org/example/scala_definitions.scala", ScalaFileTextInSamePackage)

    val fileText =
      s"""package org
         |
         |package object example {
         |  new My$CARET
         |}
         |""".stripMargin

    val (_, items) = activeLookupWithItems(fileText, CompletionType.BASIC)
    val itemsLookupStrings = items.map(_.getLookupString).toSeq
    assertContainsElements(itemsLookupStrings, Seq(
      "MyScalaClassPublic",
      "MyScalaClassPackagePrivate",
      "MyJavaClassPublic",
      "MyJavaClassPackagePrivate",
    ))
  }

  def testCompleteAfterNew_ShouldContainDefinitionsFromSamePackage_BothScalaAndJava_FromClass(): Unit = {
    myFixture.addFileToProject("org/example/scala_definitions.scala", ScalaFileTextInSamePackage)
    myFixture.addFileToProject("org/example/MyJavaClassPublic.java", JavaFileTextInSamePackage)

    val fileText =
      s"""package org.example
         |
         |class Example {
         |  new My$CARET
         |}
         |""".stripMargin

    val (_, items) = activeLookupWithItems(fileText, CompletionType.BASIC)
    val itemsLookupStrings = items.map(_.getLookupString).toSeq
    assertContainsElements(itemsLookupStrings, Seq(
      "MyScalaClassPublic",
      "MyScalaClassPackagePrivate",
      "MyJavaClassPublic",
      "MyJavaClassPackagePrivate",
    ))
  }

  private def assertContainsElements[T](collection: Seq[_ <: T], expected: Seq[_ <: T]): Unit = {
    import scala.jdk.CollectionConverters.SeqHasAsJava
    UsefulTestCase.assertContainsElements(collection.asJava, expected.asJava)
  }
}
