package org.jetbrains.plugins.scala.lang.completion.ml

import java.util

import com.intellij.codeInsight.completion.ml.{ContextFeatures, ElementFeatureProvider, MLFeatureValue}
import com.intellij.codeInsight.completion.{CodeCompletionHandlerBase, CompletionLocation, CompletionType}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.extensions._
import org.junit.{Assert, Test}

import scala.collection.mutable

class ScalaElementFeatureProviderTest extends ScalaLightCodeInsightFixtureTestAdapter {

  @Test
  def testPostfix(): Unit = {

    assertContext("postfix", MLFeatureValue.binary(true))(
      """object X {
        |  val a = 1
        |  a <caret>
        |}
        |""".stripMargin
    )

    assertContext("postfix", MLFeatureValue.binary(false))(
      """object X {
        |  val a = 1
        |  a.<caret>
        |}
        |""".stripMargin
    )
  }

  @Test
  def testInsideCatch(): Unit = {

    assertContext("inside_catch", MLFeatureValue.binary(true))(
      """object X {
        |  try a
        |  catch <caret>
        |}
        |""".stripMargin
    )

    assertContext("inside_catch", MLFeatureValue.binary(false))(
      """object X {
        |  try <caret>
        |  catch b
        |}
        |""".stripMargin
    )
  }

  @Test
  def testTypeExpected(): Unit = {
    assertContext("type_expected", MLFeatureValue.binary(true))(
      """object X {
        |  List[<caret>]
        |}
        |""".stripMargin
    )

    assertContext("type_expected", MLFeatureValue.binary(true))(
      """object X {
        |  type A = <caret>
        |}
        |""".stripMargin
    )

    assertContext("type_expected", MLFeatureValue.binary(true))(
      """object X {
        |  val a: <caret>
        |}
        |""".stripMargin
    )

    assertContext("type_expected", MLFeatureValue.binary(true))(
      """object X {
        |  class A extends <caret>
        |}
        |""".stripMargin
    )

    assertContext("type_expected", MLFeatureValue.binary(true))(
      """object X {
        |  type A = Int with <caret>
        |}
        |""".stripMargin
    )

    assertContext("type_expected", MLFeatureValue.binary(true))(
      """object X {
        |  1 match { case _: <caret> }
        |}
        |""".stripMargin
    )

    assertContext("type_expected", MLFeatureValue.binary(false))(
      """object X {
        |  <caret>
        |}
        |""".stripMargin
    )

    assertContext("type_expected", MLFeatureValue.binary(false))(
      """object X {
        |  val a = <caret>
        |}
        |""".stripMargin
    )

    assertContext("type_expected", MLFeatureValue.binary(false))(
      """object X {
        |  1.<caret>
        |}
        |""".stripMargin
    )
  }

  @Test
  def testAfterNew(): Unit = {
    assertContext("after_new", MLFeatureValue.binary(true))(
      """object X {
        |  new <caret>
        |}
        |""".stripMargin
    )

    assertContext("after_new", MLFeatureValue.binary(false))(
      """object X {
        |  new java.util.HashMap(<caret>)
        |}
        |""".stripMargin
    )
  }

  @Test
  def testKind(): Unit = {

    assertElement("kind", "type", MLFeatureValue.categorical(ItemKind.KEYWORD))(
      """object X {
        |  <caret>
        |}
        |""".stripMargin
    )

    assertElement("kind", "Nil", MLFeatureValue.categorical(ItemKind.VALUE))(
      """object X {
        |  <caret>
        |}
        |""".stripMargin
    )

    assertElement("kind", "BufferedIterator", MLFeatureValue.categorical(ItemKind.TYPE_ALIAS))(
      """object X {
        |  type a = <caret>
        |}
        |""".stripMargin
    )

    assertElement("kind", "a", MLFeatureValue.categorical(ItemKind.VARIABLE))(
      """object X {
        |  var a = 1
        |  <caret>
        |}
        |""".stripMargin
    )

    assertElement("kind", "X", MLFeatureValue.categorical(ItemKind.OBJECT))(
      """object X {
        |  <caret>
        |}
        |""".stripMargin
    )

    assertElement("kind", "X", MLFeatureValue.categorical(ItemKind.CLASS))(
      """class X {
        |  type a = <caret>
        |}
        |""".stripMargin
    )

    assertElement("kind", "X", MLFeatureValue.categorical(ItemKind.TRAIT))(
      """trait X {
        |  type a = <caret>
        |}
        |""".stripMargin
    )

    assertElement("kind", "int2Integer", MLFeatureValue.categorical(ItemKind.FUNCTION))(
      """object X {
        |  <caret>
        |}
        |""".stripMargin
    )

    assertElement("kind", "asInstanceOf", MLFeatureValue.categorical(ItemKind.SYNTHETHIC_FUNCTION))(
      """object X {
        |  "" <caret>
        |}
        |""".stripMargin
    )


    assertElement("kind", "LinkageError", MLFeatureValue.categorical(ItemKind.EXCEPTION))(
      """object X {
        |  <caret>
        |}
        |""".stripMargin
    )

    assertElement("kind", "java", MLFeatureValue.categorical(ItemKind.PACKAGE))(
      """object X {
        |  <caret>
        |}
        |""".stripMargin
    )
  }

  @Test
  def testSymbolic(): Unit = {
    assertElement("symbolic", "+", MLFeatureValue.binary(true))(
      """object X {
        |  Set.empty <caret>
        |}
        |""".stripMargin
    )

    assertElement("symbolic", "--", MLFeatureValue.binary(true))(
      """object X {
        |  Set.empty <caret>
        |}
        |""".stripMargin
    )

    assertElement("symbolic", "contains", MLFeatureValue.binary(false))(
      """object X {
        |  Set.empty <caret>
        |}
        |""".stripMargin
    )
  }

  @Test
  def testUnary(): Unit = {
    assertElement("unary", "unary_+", MLFeatureValue.binary(true))(
      """object X {
        |  1 <caret>
        |}
        |""".stripMargin
    )

    assertElement("unary", "+", MLFeatureValue.binary(false))(
      """object X {
        |  1 <caret>
        |}
        |""".stripMargin
    )
  }

  @Test
  def testScala(): Unit = {

    assertElement("scala", "List", MLFeatureValue.binary(true))(
      """object X {
        |  <caret>
        |}
        |""".stripMargin
    )

    assertElement("scala", "NoSuchMethodError", MLFeatureValue.binary(false))(
      """object X {
        |  <caret>
        |}
        |""".stripMargin
    )
  }

  @Test
  def testJavaObjectMethod(): Unit = {

    assertElement("java_object_method", "equals", MLFeatureValue.binary(true))(
      """object X {
        |  1 <caret>
        |}
        |""".stripMargin
    )

    assertElement("java_object_method", "+", MLFeatureValue.binary(false))(
      """object X {
        |  1 <caret>
        |}
        |""".stripMargin
    )

    assertElement("java_object_method", "to", MLFeatureValue.binary(false))(
      """object X {
        |  1 <caret>
        |}
        |""".stripMargin
    )
  }

  @Test
  def testArgumentCount(): Unit = {

    assertElement("argument_count", "Nil", MLFeatureValue.float(-1))(
      """object X {
        |  <caret>
        |}
        |""".stripMargin
    )

    assertElement("argument_count", "f", MLFeatureValue.float(0))(
      """object X {
        |  def f(): Unit = ???
        |  <caret>
        |}
        |""".stripMargin
    )

    assertElement("argument_count", "print", MLFeatureValue.float(1))(
      """object X {
        |  <caret>
        |}
        |""".stripMargin
    )

    assertElement("argument_count", "f", MLFeatureValue.float(2))(
      """object X {
        |  val f: (Int, Int) => Double = ???
        |  <caret>
        |}
        |""".stripMargin
    )

    assertElement("argument_count", "f", MLFeatureValue.float(0))(
      """object X {
        |  def f(implicit int: Int): Unit = ???
        |  <caret>
        |}
        |""".stripMargin
    )
  }

  private def assertContext(name: String, value: MLFeatureValue)(fileText : String): Unit = {
    val elementsFeatures = computeElementsFeatures(fileText)
    Assert.assertEquals(featuresAdapter(elementsFeatures.head._2.get(name)), featuresAdapter(value))
  }

  private def assertElement(name: String, element: String, value: MLFeatureValue)(fileText : String): Unit = {
    val elementFeatures = computeElementsFeatures(fileText)
    Assert.assertEquals(featuresAdapter(elementFeatures(element).get(name)), featuresAdapter(value))
  }

  private def computeElementsFeatures(fileText: String): Map[String, util.Map[String, MLFeatureValue]] = {
    class ScalaElementFeatureProviderWrapper extends ScalaElementFeatureProvider {
      val elements = mutable.Map.empty[String, util.Map[String, MLFeatureValue]]

      override def calculateFeatures(element: LookupElement, location: CompletionLocation, contextFeatures: ContextFeatures): util.Map[String, MLFeatureValue] = {
        val result = super.calculateFeatures(element, location, contextFeatures)

        val name = element.getObject match {
          case named: PsiNamedElement => named.name
          case _ => element.getObject.toString
        }

        elements += name -> result

        result
      }
    }

    val provider = new ScalaElementFeatureProviderWrapper
    try {
      ElementFeatureProvider.EP_NAME.addExplicitExtension(ScalaLanguage.INSTANCE, provider)

      configureFromFileText(fileText)
      changePsiAt(getEditor.getCaretModel.getOffset)
      getFixture.complete(CompletionType.BASIC, 1)

      val handler = new CodeCompletionHandlerBase(CompletionType.BASIC, false, false, true)
      handler.invokeCompletion(getProject, getEditor, 1)

      provider.elements.toMap
    }
    finally {
      ElementFeatureProvider.EP_NAME.removeExplicitExtension(ScalaLanguage.INSTANCE, provider)
    }
  }

  // no equals impl for MLFeatureValue
  private def featuresAdapter(value: MLFeatureValue): Any = {
    val actualValue = Option(value.asBinary()) orElse Option(value.asCategorical()) orElse Option(value.asFloat())
    actualValue.get
  }
}
