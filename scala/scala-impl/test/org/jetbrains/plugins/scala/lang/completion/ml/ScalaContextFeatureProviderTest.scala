package org.jetbrains.plugins.scala.lang.completion.ml

import com.intellij.codeInsight.completion.ml.{CompletionEnvironment, ContextFeatureProvider, MLFeatureValue}
import com.intellij.codeInsight.completion.{CodeCompletionHandlerBase, CompletionType}
import org.jetbrains.plugins.scala.{CompletionTests, ScalaLanguage}
import org.junit.experimental.categories.Category

import java.util

//noinspection ApiStatus,UnstableApiUsage
@Category(Array(classOf[CompletionTests]))
class ScalaContextFeatureProviderTest extends MLCompletionTest {
  
  import MLFeatureValue._

  def testLocation(): Unit = {
    import Location._

    assertFeature("location", categorical(CLASS_BODY))(
      s"""object X {
         |  $CARET
         |}
         |""".stripMargin
    )

    assertFeature("location", categorical(CLASS_BODY))(
      s"""class X {
         |  $CARET
         |}
         |""".stripMargin
    )

    assertFeature("location", categorical(CLASS_BODY))(
      s"""trait X {
         |  $CARET
         |}
         |""".stripMargin
    )

    assertFeature("location", categorical(CLASS_BODY))(
      s"""trait X {
         |  private $CARET
         |}
         |""".stripMargin
    )

    assertFeature("location", categorical(CLASS_PARENTS))(
      s"""class X extends $CARET {
         |}
         |""".stripMargin
    )

    assertFeature("location", categorical(FILE))(
      s"""$CARET
         |""".stripMargin
    )

    assertFeature("location", categorical(FILE))(
      s"""trait X {
         |
         |}
         |
         |$CARET
         |""".stripMargin
    )

    assertFeature("location", categorical(EXPRESSION))(
      s"""object X {
         |  if (true) $CARET
         |}
         |""".stripMargin
    )

    assertFeature("location", categorical(BLOCK))(
      s"""object X {
         |  if (true) {
         |    $CARET
         |  }
         |}
         |""".stripMargin
    )

    assertFeature("location", categorical(IF))(
      s"""object X {
         |  if ($CARET)
         |}
         |""".stripMargin
    )

    assertFeature("location", categorical(FOR))(
      s"""object X {
         |  for {
         |    $CARET
         |  }
         |}
         |""".stripMargin
    )

    assertFeature("location", categorical(FOR))(
      s"""object X {
         |  for ($CARET)
         |}
         |""".stripMargin
    )

    assertFeature("location", categorical(EXPRESSION))(
      s"""object X {
        |  for {
        |    _ <- List.empty
        |  }
        |  yield $CARET
        |}
        |""".stripMargin
    )

    assertFeature("location", categorical(BLOCK))(
      s"""object X {
        |  for {
        |    _ <- List.empty
        |  }
        |  yield {
        |    $CARET
        |  }
        |}
        |""".stripMargin
    )

    assertFeature("location", categorical(EXPRESSION))(
      s"""object X {
        |  try $CARET
        |}
        |""".stripMargin
    )

    assertFeature("location", categorical(BLOCK))(
      s"""object X {
        |  try {
        |    $CARET
        |  }
        |}
        |""".stripMargin
    )

    assertFeature("location", categorical(BLOCK))(
      s"""object X {
        |  try {
        |    $CARET
        |  }
        |}
        |""".stripMargin
    )

    assertFeature("location", categorical(EXPRESSION))(
      s"""object X {
        |  try {
        |    throw new Exception
        |  }
        |  finally $CARET
        |}
        |""".stripMargin
    )

    assertFeature("location", categorical(BLOCK))(
      s"""class X {
        |  def f(): Unit = {
        |    2 + 2 == 5
        |    2 + 3
        |    $CARET
        |  }
        |}
        |""".stripMargin
    )

    assertFeature("location", categorical(ARGUMENT))(
      s"""class X {
        |  println($CARET)
        |}
        |""".stripMargin
    )

    assertFeature("location", categorical(ARGUMENT))(
      s"""class X {
        |  def f(): Unit = {
        |    1 + $CARET
        |  }
        |}
        |""".stripMargin
    )

    assertFeature("location", categorical(REFERENCE))(
      s"""class X {
        |  def f(): Unit = {
        |    1.$CARET
        |  }
        |}
        |""".stripMargin
    )

    assertFeature("location", categorical(REFERENCE))(
      s"""object X {
        |  val a = 1 $CARET
        |}
        |""".stripMargin
    )

    assertFeature("location", categorical(REFERENCE))(
      s"""class X {
        |  List.empty[Int].map(_.$CARET)
        |}
        |""".stripMargin
    )

    assertFeature("location", categorical(EXPRESSION))(
      s"""class X {
        |  def f(): Unit = $CARET
        |}
        |""".stripMargin
    )

    assertFeature("location", categorical(EXPRESSION))(
      s"""class X {
        |  def f(): Unit = {
        |    val a = $CARET
        |  }
        |}
        |""".stripMargin
    )

    assertFeature("location", categorical(PARAMETER))(
      s"""class X {
        |  def f($CARET): Unit
        |}
        |""".stripMargin
    )

    assertFeature("location", categorical(UNKNOWN))(
      s"""class X {
        |  def f(): Unit = $CARET + 1
        |}
        |""".stripMargin
    )
  }

  def testPreviousKeyword(): Unit = {
    import Keyword._

    assertFeature("previous_keyword", categorical(PRIVATE))(
      s"""trait X {
        |  private $CARET
        |}
        |""".stripMargin
    )

    assertFeature("previous_keyword", categorical(ABSRACT))(
      s"""class X {
        |  protected abstract $CARET
        |}
        |""".stripMargin
    )

    assertFeature("previous_keyword", categorical(IMPLICIT))(
      s"""class X {
        |  protected abstract implicit $CARET
        |}
        |""".stripMargin
    )

    assertFeature("previous_keyword", categorical(DEF))(
      s"""class X {
        |  protected abstract implicit def $CARET
        |}
        |""".stripMargin
    )

    assertFeature("previous_keyword", categorical(LAZY))(
      s"""class X {
        |  lazy $CARET
        |}
        |""".stripMargin
    )

    assertFeature("previous_keyword", categorical(IMPORT))(
      s"""import $CARET
        |""".stripMargin
    )

    assertFeature("previous_keyword", categorical(ELSE))(
      s"""object X {
        |  if (true) 1
        |  else $CARET
        |}
        |""".stripMargin
    )
  }

  private def assertFeature(name: String, expected: MLFeatureValue)(fileText: String): Unit = {
    val features = computeFeatures(fileText)
    AssertFeatureValues.equals(expected, features.get(name))
  }

  private def computeFeatures(fileText: String): util.Map[String, MLFeatureValue] = {
    class ScalaContextFeatureProviderWrapper extends ContextFeatureProvider {

      private val original = new ScalaContextFeatureProvider

      var features: util.Map[String, MLFeatureValue] = _

      override def getName: String = original.getName

      override def calculateFeatures(environment: CompletionEnvironment): util.Map[String, MLFeatureValue] = {
        val result = original.calculateFeatures(environment)

        features = result

        result
      }
    }

    val provider = new ScalaContextFeatureProviderWrapper
    try {
      ContextFeatureProvider.EP_NAME.addExplicitExtension(ScalaLanguage.INSTANCE, provider)

      configureFromFileText(fileText)
      changePsiAt(getEditor.getCaretModel.getOffset)
      myFixture.complete(CompletionType.BASIC, 1)

      val handler = new CodeCompletionHandlerBase(CompletionType.BASIC, false, false, true)
      handler.invokeCompletion(getProject, getEditor, 1)

      provider.features
    }
    finally {
      ContextFeatureProvider.EP_NAME.removeExplicitExtension(ScalaLanguage.INSTANCE, provider)
    }
  }
}
