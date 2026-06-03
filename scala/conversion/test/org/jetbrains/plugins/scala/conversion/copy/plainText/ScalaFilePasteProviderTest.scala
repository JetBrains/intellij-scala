package org.jetbrains.plugins.scala.conversion.copy.plainText

import com.intellij.ide.{IdeView, PasteProvider}
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.actionSystem.{DataContext, LangDataKeys, PlatformCoreDataKeys}
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.PsiDirectory
import com.intellij.util.ui.TextTransferable
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.conversion.copy.plainText.ScalaFilePasteProvider.PasteActionIntention
import org.jetbrains.plugins.scala.conversion.copy.plainText.ScalaFilePasteProviderTest.DummyIdeView
import org.junit.Assert.{assertEquals, assertFalse, assertTrue, fail}

class ScalaFilePasteProviderTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  private def assertSuggestedScalaFileName(pastedText: String, expectedFileName: String): Unit = {
    assertScalaCodePasteEnabled(pastedText: String)

    val provider = new ScalaFilePasteProvider()
    val nameWithExtension = provider.calculatePasteActionOutcome(pastedText, getModule, null) match {
      case Some(value) =>
        value match {
          case PasteActionIntention.CreateNewFile(_, fileName, _) =>
            fileName
          case PasteActionIntention.UpdateExistingFile(psiFile, _, _, _) =>
            fail(s"Expected to create new file $expectedFileName but actual intention is to update existing file ${psiFile.getName}").asInstanceOf[Nothing]
        }
      case None =>
        fail("Can't create scala file for pasted code").asInstanceOf[Nothing]
    }
    assertEquals("Suggested file name", expectedFileName, nameWithExtension.fullName)
  }

  private def assertScalaCodePasteEnabled(pastedText: String): Unit = {
    val dataContext: DataContext = prepareDataContextAndGlobalCopyBuffer(pastedText)
    val provider: PasteProvider = new ScalaFilePasteProvider()
    assertTrue(s"Scala paste provider should be enabled for text: $pastedText", provider.isPasteEnabled(dataContext))
  }

  private def assertScalaCodePasteNotEnabled(pastedText: String): Unit = {
    val dataContext: DataContext = prepareDataContextAndGlobalCopyBuffer(pastedText)
    val provider: PasteProvider = new ScalaFilePasteProvider()
    assertFalse(s"Scala paste provider should not be enabled for text: $pastedText", provider.isPasteEnabled(dataContext))
  }

  def testSuggestedFileNameForClass(): Unit = {
    assertSuggestedScalaFileName("class MyClass", "MyClass.scala")
  }

  def testSuggestedFileNameForTrait(): Unit = {
    assertSuggestedScalaFileName("trait MyTrait", "MyTrait.scala")
  }

  def testSuggestedFileNameForObject(): Unit = {
    assertSuggestedScalaFileName("object MyObject", "MyObject.scala")
  }

  def testSuggestedFileNameForType(): Unit = {
    assertSuggestedScalaFileName("type MyTypeAlias = AliasedClass", "MyTypeAlias.scala")
  }

  def testSuggestedFileNameForDef(): Unit = {
    assertSuggestedScalaFileName("def myFunction: Int = ???", "myFunction.scala")
  }

  def testSuggestedFileNameForVal(): Unit = {
    assertSuggestedScalaFileName("val myValue: Int = ???", "myValue.scala")
  }

  def testSuggestedFileNameForVar(): Unit = {
    assertSuggestedScalaFileName("var myVariable: Int = ???", "myVariable.scala")
  }

  def testSuggestedFileNameForValMultipleBindings(): Unit = {
    assertSuggestedScalaFileName("val (myValueFromPattern1, myValueFromPattern2) = (???, ???)", "myValueFromPattern1.scala")
  }

  def testSuggestedFileNameForVarMultipleBindings(): Unit = {
    assertSuggestedScalaFileName("var (myVariableFromPattern1, myVariableFromPattern2) = (???, ???)", "myVariableFromPattern1.scala")
  }

  def testSuggestedFileNameForEnum(): Unit = {
    assertSuggestedScalaFileName(
      """enum MyEnum:
        |  case MyCase1, MyCase2""".stripMargin,
      "MyEnum.scala"
    )
  }

  def testSuggestedFileNameForGiven(): Unit = {
    assertSuggestedScalaFileName("given myGiven: String = ???", "myGiven.scala")
  }

  def testSuggestedFileNameFileWithoutMembersButWithPackage_WithImports(): Unit = {
    assertSuggestedScalaFileName(
      """package org.example
        |
        |import org.example.O.*
        |""".stripMargin, "definitions.scala")
  }

  def testSuggestedFileNameFileWithoutMembersButWithPackage_WithExports(): Unit = {
    assertSuggestedScalaFileName(
      """package org.example
        |
        |export org.example.O.*
        |""".stripMargin, "definitions.scala")
  }

  def testSuggestedFileNameForExtensions(): Unit = {
    assertSuggestedScalaFileName(
      """extension (s: String)
        |  def myExtension1: String = ???
        |  def myExtension2: String = ???""".stripMargin,
      "myExtension1.scala"
    )
  }

  def testSuggestedFileNameForExpression(): Unit = {
    assertSuggestedScalaFileName("println(42)", "worksheet.sc")
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

  def testCodeWithSemicolonWithScalaSpecificKeywordsDef(): Unit = {
    assertSuggestedScalaFileName("class MyClass { def myPrint(): Unit = { println(1); println(2) } }", "MyClass.scala")
  }

  def testCodeWithSemicolonWithScalaSpecificKeywordsTrait(): Unit = {
    assertSuggestedScalaFileName("trait MyTrait { println(1); println(2) }", "MyTrait.scala")
  }

  def testCodeWithSemicolonWithoutScalaSpecificKeywords(): Unit = {
    assertScalaCodePasteNotEnabled("class MyClass { println(1) ; println(2) }")
  }
}

object ScalaFilePasteProviderTest {
  private class DummyIdeView extends IdeView {
    override def getDirectories: Array[PsiDirectory] = null

    override def getOrChooseDirectory(): PsiDirectory = null
  }
}