package org.jetbrains.plugins.scala
package lang
package completion
package ml

import com.intellij.codeInsight.completion.ml.{ContextFeatures, ElementFeatureProvider, MLFeatureValue}
import com.intellij.codeInsight.completion.{CodeCompletionHandlerBase, CompletionLocation, CompletionType}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.extensions._
import org.junit.experimental.categories.Category

import java.util
import scala.collection.mutable

@Category(Array(classOf[CompletionTests]))
class ScalaElementFeatureProviderTest extends ScalaLightCodeInsightFixtureTestAdapter {

  import MLFeatureValue._

  def testPostfix(): Unit = {

    assertContext("postfix", binary(true))(
      s"""object X {
         |  val a = 1
         |  a $CARET
         |}
         |""".stripMargin
    )

    assertContext("postfix", binary(false))(
      s"""object X {
         |  val a = 1
         |  a.$CARET
         |}
         |""".stripMargin
    )
  }

  def testInsideCatch(): Unit = {

    assertContext("inside_catch", binary(true))(
      s"""object X {
         |  try a
         |  catch $CARET
         |}
         |""".stripMargin
    )

    assertContext("inside_catch", binary(false))(
      s"""object X {
         |  try $CARET
         |  catch b
         |}
         |""".stripMargin
    )
  }

  def testTypeExpected(): Unit = {
    assertContext("type_expected", binary(true))(
      s"""object X {
         |  List[$CARET]
         |}
         |""".stripMargin
    )

    assertContext("type_expected", binary(true))(
      s"""object X {
         |  type A = $CARET
         |}
         |""".stripMargin
    )

    assertContext("type_expected", binary(true))(
      s"""object X {
         |  val a: $CARET
         |}
         |""".stripMargin
    )

    assertContext("type_expected", binary(true))(
      s"""object X {
         |  class A extends $CARET
         |}
         |""".stripMargin
    )

    assertContext("type_expected", binary(true))(
      s"""object X {
         |  type A = Int with $CARET
         |}
         |""".stripMargin
    )

    assertContext("type_expected", binary(true))(
      s"""object X {
         |  1 match { case _: $CARET }
         |}
         |""".stripMargin
    )

    assertContext("type_expected", binary(true))(
      s"""object X {
        |  def f(): $CARET
        |}
        |""".stripMargin
    )

    assertContext("type_expected", binary(false))(
      s"""object X {
         |  $CARET
         |}
         |""".stripMargin
    )

    assertContext("type_expected", binary(false))(
      s"""object X {
         |  val a = $CARET
         |}
         |""".stripMargin
    )

    assertContext("type_expected", binary(false))(
      s"""object X {
         |  1.$CARET
         |}
         |""".stripMargin
    )
  }

  def testAfterNew(): Unit = {
    assertContext("after_new", binary(true))(
      s"""object X {
         |  new $CARET
         |}
         |""".stripMargin
    )

    assertContext("after_new", binary(false))(
      s"""object X {
         |  new java.util.HashMap($CARET)
         |}
         |""".stripMargin
    )
  }

  def testKind(): Unit = {
    import CompletionItem._

    assertElement("kind", "type", categorical(KEYWORD))(
      s"""object X {
         |  $CARET
         |}
         |""".stripMargin
    )

    assertElement("kind", "Traversable", categorical(VALUE))(
      s"""object X {
         |  $CARET
         |}
         |""".stripMargin
    )

    assertElement("kind", "TraversableOnce", categorical(TYPE_ALIAS))(
      s"""object X {
         |  type a = $CARET
         |}
         |""".stripMargin
    )

    assertElement("kind", "a", categorical(VARIABLE))(
      s"""object X {
         |  var a = 1
         |  $CARET
         |}
         |""".stripMargin
    )

    assertElement("kind", "X", categorical(OBJECT))(
      s"""object X {
         |  $CARET
         |}
         |""".stripMargin
    )

    assertElement("kind", "X", categorical(CLASS))(
      s"""class X {
         |  type a = $CARET
         |}
         |""".stripMargin
    )

    assertElement("kind", "X", categorical(TRAIT))(
      s"""trait X {
         |  type a = $CARET
         |}
         |""".stripMargin
    )

    assertElement("kind", "int2Integer", categorical(FUNCTION))(
      s"""object X {
         |  $CARET
         |}
         |""".stripMargin
    )

    assertElement("kind", "asInstanceOf", categorical(SYNTHETHIC_FUNCTION))(
      s"""object X {
         |  "" $CARET
         |}
         |""".stripMargin
    )


    assertElement("kind", "LinkageError", categorical(EXCEPTION))(
      s"""object X {
         |  $CARET
         |}
         |""".stripMargin
    )

    assertElement("kind", "java", categorical(PACKAGE))(
      s"""object X {
         |  $CARET
         |}
         |""".stripMargin
    )
  }

  def testKeyword(): Unit = {
    import Keyword._

    assertElement("keyword", "import", categorical(IMPORT))(
      s"""$CARET
        |""".stripMargin
    )

    assertElement("keyword", "import", categorical(IMPORT))(
      s"""object X {
        |  $CARET
        |}
        |""".stripMargin
    )

    assertElement("keyword", "val", categorical(VAL))(
      s"""object X {
        |  $CARET
        |}
        |""".stripMargin
    )

    assertElement("keyword", "var", categorical(VAR))(
      s"""object X {
        |  $CARET
        |}
        |""".stripMargin
    )

    assertElement("keyword", "class", categorical(CLASS))(
      s"""object X {
        |  $CARET
        |}
        |""".stripMargin
    )

    assertElement("keyword", "if", categorical(IF))(
      s"""object X {
        |  $CARET
        |}
        |""".stripMargin
    )

    assertElement("keyword", "if", categorical(IF))(
      s"""object X {
        |  if (true) {
        |  }
        |  else $CARET
        |}
        |""".stripMargin
    )

    assertElement("keyword", "else", categorical(ELSE))(
      s"""object X {
        |  if (true) {
        |  }
        |  $CARET
        |}
        |""".stripMargin
    )

    assertElement("keyword", "yield", categorical(YIELD))(
      s"""object X {
        |  for {
        |    _ <- List.empty
        |  }
        |  $CARET
        |}
        |""".stripMargin
    )

    assertElement("keyword", "else", categorical(ELSE))(
      s"""object X {
        |  if (true) {
        |  }
        |  $CARET
        |}
        |""".stripMargin
    )

    assertElement("keyword", "try", categorical(TRY))(
      s"""object X {
         |  $CARET
         |}
         |""".stripMargin
    )

    assertElement("keyword", "catch", categorical(CATCH))(
      s"""object X {
        |  try {
        |  }
        |  $CARET
        |}
        |""".stripMargin
    )
  }

  def testSymbolic(): Unit = {
    assertElement("symbolic", "+", binary(true))(
      s"""object X {
         |  Set.empty $CARET
         |}
         |""".stripMargin
    )

    assertElement("symbolic", "--", binary(true))(
      s"""object X {
         |  Set.empty $CARET
         |}
         |""".stripMargin
    )

    assertElement("symbolic", "contains", binary(false))(
      s"""object X {
         |  Set.empty $CARET
         |}
         |""".stripMargin
    )
  }

  def testUnary(): Unit = {
    assertElement("unary", "unary_+", binary(true))(
      s"""object X {
         |  1 $CARET
         |}
         |""".stripMargin
    )

    assertElement("unary", "+", binary(false))(
      s"""object X {
         |  1 $CARET
         |}
         |""".stripMargin
    )
  }

  def testScala(): Unit = {

    assertElement("scala", "List", binary(true))(
      s"""object X {
         |  $CARET
         |}
         |""".stripMargin
    )

    assertElement("scala", "NoSuchMethodError", binary(false))(
      s"""object X {
         |  $CARET
         |}
         |""".stripMargin
    )
  }

  def testJavaObjectMethod(): Unit = {

    assertElement("java_object_method", "equals", binary(true))(
      s"""object X {
         |  1 $CARET
         |}
         |""".stripMargin
    )

    assertElement("java_object_method", "+", binary(false))(
      s"""object X {
         |  1 $CARET
         |}
         |""".stripMargin
    )

    assertElement("java_object_method", "to", binary(false))(
      s"""object X {
         |  1 $CARET
         |}
         |""".stripMargin
    )
  }

  def testArgumentCount(): Unit = {

    assertElement("argument_count", "Nil", float(-1.0))(
      s"""object X {
         |  $CARET
         |}
         |""".stripMargin
    )

    assertElement("argument_count", "f", float(0.0))(
      s"""object X {
         |  def f(): Unit = ???
         |  $CARET
         |}
         |""".stripMargin
    )

    assertElement("argument_count", "print", float(1.0))(
      s"""object X {
         |  $CARET
         |}
         |""".stripMargin
    )

    assertElement("argument_count", "f", float(2))(
      s"""object X {
         |  val f: (Int, Int) => Double = ???
         |  $CARET
         |}
         |""".stripMargin
    )

    assertElement("argument_count", "f", float(0))(
      s"""object X {
         |  def f(implicit int: Int): Unit = ???
         |  $CARET
         |}
         |""".stripMargin
    )
  }

  def testNameNameDist(): Unit = {

    assertElement("name_name_sim", "List", float(-1.0))(
      s"""object X {
         |  val l = $CARET
         |}
         |""".stripMargin
    )

    assertElement("name_name_sim", "List", float(1.0))(
      s"""object X {
         |  val list = $CARET
         |}
         |""".stripMargin
    )

    assertElement("name_name_sim", "ScalaReflectionException", float(1.0))(
      s"""object X {
         |  var scalaReflectionException = $CARET
         |}
         |""".stripMargin
    )

    assertElement("name_name_sim", "ind", float(0.6))(
      s"""object X {
         |  val ind = ???
         |  "".charAt(index = $CARET)
         |}
         |""".stripMargin
    )

    assertElement("name_name_sim", "List", float(0.5))(
      s"""object X {
         |  def byteList = $CARET
         |}
         |""".stripMargin
    )

    assertElement("name_name_sim", "List", float(0.25))(
      s"""object X {
         |  type ByteListTypeName = $CARET
         |}
         |""".stripMargin
    )

    assertElement("name_name_sim", "List", float(0.5))(
      s"""object X {
         |  def f(listname: Nothing) = ???
         |  f($CARET)
         |}
         |""".stripMargin
    )

    // TODO for some reason expectedTypeEx don't return name for non local methods
    //    assertElement("name_name_sim", "requ", float(0.5))(
    //      s"""object X {
    //        |  val requ = ???
    //        |  require($CARET)
    //        |}
    //        |""".stripMargin
    //    )
  }

  def testNameTypeDist(): Unit = {

    assertElement("name_type_sim", "Array", float(-1.0))(
      s"""object X {
         |  type A = $CARET
         |}
         |""".stripMargin
    )

    assertElement("name_type_sim", "List", float(0.25))(
      s"""object X {
         |  type ByteListTypeName = $CARET
         |}
         |""".stripMargin
    )

    assertElement("name_type_sim", "Product12", float(1.0))(
      s"""object X {
         |  def f(product11: $CARET)
         |}
         |""".stripMargin
    )

    assertElement("name_type_sim", "List", float(1.0))(
      s"""object X {
         |  val list: $CARET
         |}
         |""".stripMargin
    )

    assertElement("name_type_sim", "a", float(0.5))(
      s"""object X {
         |  val a: List[Int] = ???
         |  def f(listname: Nothing) = ???
         |  f($CARET)
         |}
         |""".stripMargin
    )
  }

  def testTypeNameDist(): Unit = {

    assertElement("type_name_sim", "Array", float(-1.0))(
      s"""object X {
         |  val a = $CARET
         |}
         |""".stripMargin
    )

    assertElement("type_name_sim", "integral", float(0.375))(
      s"""object X {
         |  val integral = ???
         |  val b: Int = $CARET
         |}
         |""".stripMargin
    )

    assertElement("type_name_sim", "index", float(0.4))(
      s"""object X {
         |  val index = ???
         |  "".charAt($CARET)
         |}
         |""".stripMargin
    )

    // TODO for some reason expectedTypeEx don't return any type for overloading
    //    assertElement("name_name_sim", "requ", float(0.5))(
    //      s"""object X {
    //        |  val string = ???
    //        |  "".indexOf($CARET)
    //        |}
    //        |""".stripMargin
    //    )
  }

  def testTypeTypeDist(): Unit = {

    assertElement("type_type_sim", "integer", float(-1.0))(
      s"""object X {
         |  type I = Int
         |  val integer: Int = ???
         |  val anotherInteger: I = $CARET
         |}
         |""".stripMargin
    )

    assertElement("type_type_sim", "a", float(0.5))(
      s"""object X {
         |  val a: Option[Int] = ???
         |  var b: Int with Double = $CARET
         |}
         |""".stripMargin
    )

    assertElement("type_type_sim", "a", float(1.0))(
      s"""object X {
         |  val a: Option[Int] = ???
         |  def f(o: Option[Int]) = ???
         |  f($CARET)
         |}
         |""".stripMargin
    )

    assertElement("type_type_sim", "a", float(1.0))(
      s"""object X {
         |  val a: Option[Int] = ???
         |  val b: Int = $CARET
         |}
         |""".stripMargin
    )

    assertElement("type_type_sim", "true", float(1.0))(
      s"""object X {
        |  def f(x: Boolean): Unit = ???
        |  f($CARET)
        |}
        |""".stripMargin
    )
  }

  private def assertContext(name: String, expected: MLFeatureValue)(fileText: String): Unit = {
    val elementsFeatures = computeElementsFeatures(fileText)
    AssertFeatureValues.equals(expected, elementsFeatures.head._2.get(name))
  }

  private def assertElement(name: String, element: String, expected: MLFeatureValue)(fileText: String): Unit = {
    val elementFeatures = computeElementsFeatures(fileText)
    AssertFeatureValues.equals(expected, elementFeatures(element).get(name))
  }

  private def computeElementsFeatures(fileText: String): Map[String, util.Map[String, MLFeatureValue]] = {
    class ScalaElementFeatureProviderWrapper extends ElementFeatureProvider {

      private val original = new ScalaElementFeatureProvider

      val elements = mutable.Map.empty[String, util.Map[String, MLFeatureValue]]

      override def getName: String = original.getName

      override def calculateFeatures(element: LookupElement, location: CompletionLocation, contextFeatures: ContextFeatures): util.Map[String, MLFeatureValue] = {
        val result = original.calculateFeatures(element, location, contextFeatures)

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
      changePsiAt(getEditorOffset)
      myFixture.complete(CompletionType.BASIC, 1)

      val handler = new CodeCompletionHandlerBase(CompletionType.BASIC, false, false, true)
      handler.invokeCompletion(getProject, getEditor, 1)

      provider.elements.toMap
    }
    finally {
      ElementFeatureProvider.EP_NAME.removeExplicitExtension(ScalaLanguage.INSTANCE, provider)
    }
  }
}
