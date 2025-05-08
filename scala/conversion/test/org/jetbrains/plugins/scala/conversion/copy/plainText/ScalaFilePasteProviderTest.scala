package org.jetbrains.plugins.scala.conversion.copy.plainText

import com.intellij.ide.{IdeView, PasteProvider}
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.actionSystem.{DataContext, LangDataKeys, PlatformCoreDataKeys}
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.PsiDirectory
import com.intellij.util.ui.TextTransferable
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.conversion.copy.plainText.ScalaFilePasteProviderTest.DummyIdeView
import org.junit.Assert.{assertEquals, assertFalse, assertTrue, fail}

class ScalaFilePasteProviderTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  private def assertSuggestedFileName(pastedCode: String, expectedFileName: String): Unit = {
    val provider = new ScalaFilePasteProvider()
    val nameWithExtension = provider.suggestedScalaFileNameForText(pastedCode, getModule).getOrElse {
      fail("Can't create scala file for pasted code").asInstanceOf[Nothing]
    }
    assertEquals("Suggested file name", expectedFileName, nameWithExtension.fullName)
  }

  def testSuggestedFileNameForClass(): Unit = {
    assertSuggestedFileName("class MyClass", "MyClass.scala")
  }

  def testSuggestedFileNameForTrait(): Unit = {
    assertSuggestedFileName("trait MyTrait", "MyTrait.scala")
  }

  def testSuggestedFileNameForObject(): Unit = {
    assertSuggestedFileName("object MyObject", "MyObject.scala")
  }

  def testSuggestedFileNameForType(): Unit = {
    assertSuggestedFileName("type MyTypeAlias = AliasedClass", "MyTypeAlias.scala")
  }

  def testSuggestedFileNameForDef(): Unit = {
    assertSuggestedFileName("def myFunction: Int = ???", "myFunction.scala")
  }

  def testSuggestedFileNameForVal(): Unit = {
    assertSuggestedFileName("val myValue: Int = ???", "myValue.scala")
  }

  def testSuggestedFileNameForVar(): Unit = {
    assertSuggestedFileName("var myVariable: Int = ???", "myVariable.scala")
  }

  def testSuggestedFileNameForValMultipleBindings(): Unit = {
    assertSuggestedFileName("val (myValueFromPattern1, myValueFromPattern2) = (???, ???)", "myValueFromPattern1.scala")
  }

  def testSuggestedFileNameForVarMultipleBindings(): Unit = {
    assertSuggestedFileName("var (myVariableFromPattern1, myVariableFromPattern2) = (???, ???)", "myVariableFromPattern1.scala")
  }

  def testSuggestedFileNameForEnum(): Unit = {
    assertSuggestedFileName(
      """enum MyEnum:
        |  case MyCase1, MyCase2""".stripMargin,
      "MyEnum.scala"
    )
  }

  def testSuggestedFileNameForGiven(): Unit = {
    assertSuggestedFileName("given myGiven: String = ???", "myGiven.scala")
  }

  def testSuggestedFileNameFileWithoutMembersButWithPackage_WithImports(): Unit = {
    assertSuggestedFileName(
      """package org.example
        |
        |import org.example.O.*
        |""".stripMargin, "definitions.scala")
  }

  def testSuggestedFileNameFileWithoutMembersButWithPackage_WithExports(): Unit = {
    assertSuggestedFileName(
      """package org.example
        |
        |export org.example.O.*
        |""".stripMargin, "definitions.scala")
  }

  def testSuggestedFileNameForExtensions(): Unit = {
    assertSuggestedFileName(
      """extension (s: String)
        |  def myExtension1: String = ???
        |  def myExtension2: String = ???""".stripMargin,
      "myExtension1.scala"
    )
  }

  def testSuggestedFileNameForExpression(): Unit = {
    assertSuggestedFileName("println(42)", "worksheet.sc")
  }

  private def assertScalaCodePasteEnabled(text: String): Unit = {
    val dataContext: DataContext = prepareDataContextAndGlobalCopyBuffer(text)
    val provider: PasteProvider = new ScalaFilePasteProvider()
    assertTrue(s"Scala paste provider should be enabled for text: $text", provider.isPasteEnabled(dataContext))
  }

  private def assertScalaCodePasteNotEnabled(text: String): Unit = {
    val dataContext: DataContext = prepareDataContextAndGlobalCopyBuffer(text)
    val provider: PasteProvider = new ScalaFilePasteProvider()
    assertFalse(s"Scala paste provider should not be enabled for text: $text", provider.isPasteEnabled(dataContext))
  }

  private def prepareDataContextAndGlobalCopyBuffer(text: String): DataContext = {
    val dataContext = SimpleDataContext.builder()
      .add(PlatformCoreDataKeys.MODULE, getModule)
      .add(LangDataKeys.IDE_VIEW, new DummyIdeView)
      .build()

    // modify global paste buffer
    CopyPasteManager.getInstance.setContents(new TextTransferable(text))

    dataContext
  }

  def testAllowPastingScalaCodeWithIncompleteDefinitionsWithAssignments(): Unit = {
    assertScalaCodePasteEnabled(
      """class Wrapper {
        |  def this(i: Int) = this()
        |  def foo = //todo: implement (without type annotation)
        |  def foo: String = //todo: implement (with type annotation, left here for detecting regressions, see SCL-23798)
        |  var mVar = //todo: implement
        |  val mVal = //todo: implement
        |  lazy val mLazyVal = //todo: implement
        |  type X = //todo: implement
        |
        |  // Scala 3
        |  given stringParser: StringParser[String] = //todo: implement
        |}
        |""".stripMargin
    )
  }

  //SCL-11177
  def testDoNotTreatJavaCodeAsScala_NoTopLevelJavaClass(): Unit = {
    assertScalaCodePasteNotEnabled(
      """Person mr = new Person("Bob", "Dope");""".stripMargin
    )
  }

  def testDoNotTreatJavaCodeAsScala_NoParserErrors_1(): Unit = {
    assertScalaCodePasteNotEnabled(
      """public class HelloWorld {
        |    public static void main(String[] args) {
        |        System.out.println("Hello, world!");
        |    }
        |}""".stripMargin
    )
  }

  def testDoNotTreatJavaCodeAsScala_NoParserErrors_2(): Unit = {
    assertScalaCodePasteNotEnabled(
      """public class Factorial {
        |    public static int factorial(int n) {
        |        if (n <= 1) return 1;
        |        return n * factorial(n - 1);
        |    }
        |
        |    public static void main(String[] args) {
        |        System.out.println(factorial(5));
        |    }
        |}""".stripMargin
    )
  }

  def testDoNotTreatJavaCodeAsScala_NoParserErrors_3(): Unit = {
    assertScalaCodePasteNotEnabled(
      """public class Person {
        |    String name;
        |
        |    public Person(String name) {
        |        this.name = name;
        |    }
        |
        |    public void sayHello() {
        |        System.out.println("Hello, " + name);
        |    }
        |}""".stripMargin
    )
  }

  def testDoNotTreatJavaCodeAsScala_WithParserErrors_1(): Unit = {
    assertScalaCodePasteNotEnabled(
      """public class Sample {
        |    public static void main(String[] args) {
        |        System.out.println("Missing semicolon")
        |    }
        |}""".stripMargin
    )
  }

  def testDoNotTreatJavaCodeAsScala_WithParserErrors_2(): Unit = {
    assertScalaCodePasteNotEnabled(
      """public class Broken {
        |    public static void main(String[] args {
        |        System.out.println("Unmatched paren"
        |    }
        |}""".stripMargin
    )
  }

  def testDoNotTreatJavaCodeAsScala_WithParserErrors_3(): Unit = {
    assertScalaCodePasteNotEnabled(
      """public class Example {
        |    public void greet(String name) {
        |        System.out.println("Hello, " + name)
        |    }
        |
        |    public static void main(String[] args)
        |        greet("world");
        |}""".stripMargin
    )
  }

  def testDoNotTreatJavaCodeAsScala_WithParserErrors_4(): Unit = {
    assertScalaCodePasteNotEnabled(
      """public class Logic {
        |    public static void main(String[] args)
        |        int x = 10
        |        if (x > 5 {
        |            System.out.println("Big");
        |        else
        |            System.out.println("Small");
        |    }
        |}""".stripMargin
    )
  }

  def testDoNotTreatJavaCodeAsScala_WithParserErrors_5(): Unit = {
    assertScalaCodePasteNotEnabled(
      """public class Calc {
        |    public static void main(String args) {
        |        int a = 5;
        |        int b = ;
        |        int c = a + b
        |        System.out.println(c
        |    }""".stripMargin
    )
  }

  def testDoNotTreatJavaCodeAsScala_WithParserErrors_6(): Unit = {
    assertScalaCodePasteNotEnabled(
      """public class ErrorBox {
        |    public static void main(String[] args {
        |        String name = "Sam"
        |        int age = "twenty";
        |        System.out.println("Name: " + name);
        |        System.out.println("Age: " + age)
        |    }""".stripMargin
    )
  }

  def testDoNotTreatJavaCodeAsScala_WithParserErrors_7(): Unit = {
    assertScalaCodePasteNotEnabled(
      """public class Fail {
        |    public static void main(String[] args)
        |        int x = 5
        |        int y = 10
        |        int sum = x + y
        |        System.out.println("Sum is: " + sum)
        |
        |    private void helper() {
        |        System.out.println("This is helper"
        |}""".stripMargin
    )
  }
}

object ScalaFilePasteProviderTest {
  private class DummyIdeView extends IdeView {
    override def getDirectories: Array[PsiDirectory] = null

    override def getOrChooseDirectory(): PsiDirectory = null
  }
}