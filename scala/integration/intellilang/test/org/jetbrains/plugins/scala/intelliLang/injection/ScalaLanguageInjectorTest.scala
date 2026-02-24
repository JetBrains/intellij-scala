package org.jetbrains.plugins.scala.intelliLang.injection

import com.intellij.patterns.PsiJavaPatterns
import com.intellij.patterns.compiler.PatternCompilerImpl.LazyPresentablePattern
import org.intellij.plugins.intelliLang.inject.config.{BaseInjection, InjectionPlace}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.intelliLang.injection.InjectionTestUtils.*
import org.jetbrains.plugins.scala.patterns.ScalaPatterns
import org.junit.Assert.*

import scala.jdk.CollectionConverters.*

class ScalaLanguageInjectorTest_Scala2 extends InjectionInBodyTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  ////////////////////////////////////////
  // @Language annotation injection tests
  ////////////////////////////////////////

  def testAnnotationInjection_Scala2Language(): Unit = {
    val body =
      raw"""def foo(@Language("Scala") param: String): Unit = ???
           |foo("class A ${CARET}extends AnyRef")
           |""".stripMargin

    val expected =
      """class A extends AnyRef"""

    doAnnotationTestInBody(ScalaLangId, body, expected)
  }

  def testAnnotationInjection_Scala3Language(): Unit = {
    val body =
      raw"""def foo(@Language("Scala 3") param: String): Unit = ???
           |foo("enum MyEnum $CARET{ case A, B; case C } ; given value: String = ???")
           |""".stripMargin

    val expected =
      """enum MyEnum { case A, B; case C } ; given value: String = ???"""

    doAnnotationTestInBody(Scala3LangId, body, expected)
  }

  def testAnnotationInjection_InClassConstructor(): Unit = {
    doAnnotationTestInBody(
      ScalaLangId,
      s"""class MyClass(
         |  @Language("Scala") param: String
         |)
         |
         |new MyClass("${CARET}val x = 0")
         |""".stripMargin,
      "val x = 0"
    )
  }

  def testAnnotationInjection_InClassConstructor_JavaClass(): Unit = {
    getFixture.addFileToProject("MyJavaClass.java",
      //language=Java
      """public class MyJavaClass {
        |    MyJavaClass(@Language("Scala") String param) {
        |    }
        |}
        |""".stripMargin
    )
    doAnnotationTestInBody(
      ScalaLangId,
      s"""new MyJavaClass("val x = 0")""",
      "val x = 0"
    )
  }

  def testAnnotationInjection_InClassSecondaryConstructor(): Unit = {
    doAnnotationTestInBody(
      ScalaLangId,
      s"""class MyClass(
         |  @Language("Scala") param: String
         |) {
         |  def this(@Language("Scala") param: String, x: Int) = this(param)
         |}
         |
         |new MyClass("${CARET}val x = 0", 42)
         |""".stripMargin,
      "val x = 0"
    )
  }

  def testAnnotationInjection_InApplyMethod(): Unit = {
    doAnnotationTestInBody(
      ScalaLangId,
      s"""class MyClass(
         |  param: String
         |)
         |
         |object MyClass {
         |  def apply(@Language("Scala") param: String): MyClass = ???
         |}
         |
         |MyClass("${CARET}val x = 0")
         |""".stripMargin,
      "val x = 0"
    )
  }

  def testAnnotationInjection_InCaseClassConstructor_CalledViaNew(): Unit = {
    doAnnotationTestInBody(
      ScalaLangId,
      s"""case class MyCaseClass(
         |  @Language("Scala") param: String
         |)
         |
         |new MyCaseClass("${CARET}val x = 0")
         |""".stripMargin,
      "val x = 0"
    )
  }

  def testAnnotationInjection_InCaseClassSecondaryConstructor_CalledViaNew(): Unit = {
    doAnnotationTestInBody(
      ScalaLangId,
      s"""case class MyCaseClass(
         |  @Language("Scala") param: String
         |) {
         |  def this(@Language("Scala") param: String, x: Int) = this(param)
         |}
         |
         |new MyCaseClass("${CARET}val x = 0", 42)
         |""".stripMargin,
      "val x = 0"
    )
  }

  def testAnnotationInjection_InCaseClassConstructor_CalledViaApply(): Unit = {
    doAnnotationTestInBody(
      ScalaLangId,
      s"""case class MyCaseClass(
         |  @Language("Scala") param: String
         |)
         |
         |MyCaseClass("${CARET}val x = 0")
         |""".stripMargin,
      "val x = 0"
    )
  }

  ////////////////////////////////////////
  // other
  ////////////////////////////////////////

  def testThatAllInjectionPatternsAreCompiled(): Unit = {
    val injections: Seq[BaseInjection] = scalaInjectionTestFixture.intelliLangConfig.getInjections("scala").asScala.toSeq
    for {
      injection <- injections
      place: InjectionPlace <- injection.getInjectionPlaces
    } {
      // for now if pattern compilation fails IntelliJ only generates warning in logs but continue to work properly
      // we would like to detect compilation failure in tests
      val pattern = place.getElementPattern match {
        case laz: LazyPresentablePattern[_] =>
          // in case of failure `PatternCompilerImpl.onCompilationFailed` will be called and test will fail
          laz.getCompiledPattern
        case p => p
      }
      if (pattern.getClass.getName.contains("False")) {
        fail(s"injection `${injection.getDisplayName}` has non-compiled pattern `${place.getText}`")
      }
    }
  }

  ///////////////////////////////////
  // Injections via patterns defined in `scalaInjections.xml`
  ///////////////////////////////////


  def testPatternInjection_Regexp_MultilineOnSingleLine(): Unit = {
    val body =
      s"""$Quotes hello world$Quotes.r""".stripMargin

    val expected =
      """ hello world"""

    doTestInBody(RegexpLangId, body, expected)
  }

  def testPatternInjection_Regexp_Multiline(): Unit = {
    val body =
      s"""class A {
         |  ${Quotes}hello
         |  world
         |!$Quotes.r
         |}
         |""".stripMargin

    val expected =
      """hello
        |  world
        |!""".stripMargin

    scalaInjectionTestFixture.doTest(RegexpLangId, body, expected)
  }

  def testPatternInjection_JavaMethodsPattern_String_matches(): Unit = {
    val body = s""""42".matches("[0-9]+\\\\d+$CARET")""".stripMargin
    val expected = """[0-9]+\d+""".stripMargin
    scalaInjectionTestFixture.doTest(RegexpLangId, body, expected)
  }

  def testPatternInjection_JavaMethodsPattern_String_replaceAll(): Unit = {
    val body = s""""42".replaceAll("[0-9]+\\\\d+$CARET", "23")""".stripMargin
    val expected = """[0-9]+\d+""".stripMargin
    scalaInjectionTestFixture.doTest(RegexpLangId, body, expected)
  }

  def testPatternInjection_JavaMethodsPattern_Pattern_compile(): Unit = {
    val body = """java.util.regex.Pattern.compile("[0-9]+\\d+")""".stripMargin
    val expected = """[0-9]+\d+""".stripMargin
    scalaInjectionTestFixture.doTest(RegexpLangId, body, expected)
  }

  //TODO: s trip margin + pattern not supported yet
  //  def test PatternInjection_Multiline_WithMargins(): Unit = {
  //    val body =
  //      s"""${Quotes}hello
  //         |  |  world
  //         |  |!$Quotes.stripMargin.r
  //         |""".stripMargin
  //
  //    val expected =
  //      """hello
  //        |  world
  //        |!""".stripMargin
  //
  //    doTestInBody(RegexpLangId, body, expected)
  //  }
}

class ScalaLanguageInjectorTest_Scala3 extends ScalaLanguageInjectorTest_Scala2 {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3


  def testAnnotationInjection_InClassConstructor_CalledViaUniversalApply(): Unit = {
    doAnnotationTestInBody(
      ScalaLangId,
      s"""class MyClass(
         |  @Language("Scala") param: String
         |)
         |
         |MyClass("${CARET}val x = 0")
         |""".stripMargin,
      "val x = 0"
    )
  }

  def testAnnotationInjection_InClassSecondaryConstructor_CalledViaUniversalApply(): Unit = {
    doAnnotationTestInBody(
      ScalaLangId,
      s"""class MyClass(
         |  @Language("Scala") param: String
         |) {
         |  def this(@Language("Scala") param: String, x: Int) = this(param)
         |}
         |
         |MyClass("${CARET}val x = 0", 42)
         |""".stripMargin,
      "val x = 0"
    )
  }

  def testAnnotationInjection_InClassConstructor_JavaClass_CalledViaUniversalApply(): Unit = {
    getFixture.addFileToProject("MyJavaClass.java",
      //language=Java
      """public class MyJavaClass {
        |    MyJavaClass(@Language("Scala") String param) {
        |    }
        |}
        |""".stripMargin
    )
    doAnnotationTestInBody(
      ScalaLangId,
      s"""MyJavaClass("val x = 0")""",
      "val x = 0"
    )
  }
}

class ScalaLanguageInjectorTest_CallArgumentPattern extends InjectionInBodyTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  private var testInjection: BaseInjection = scala.compiletime.uninitialized

  override def setUp(): Unit = {
    super.setUp()
    val methodPattern = PsiJavaPatterns.psiMethod().withName("myMethod").definedInClass("A")
    val place = new InjectionPlace(ScalaPatterns.scalaLiteral().callArgument(0, methodPattern), true)
    testInjection = new BaseInjection("scala")
    testInjection.setInjectedLanguageId(RegexpLangId)
    testInjection.setInjectionPlaces(place)
    scalaInjectionTestFixture.intelliLangConfig.replaceInjections(
      List(testInjection).asJava,
      List.empty[BaseInjection].asJava,
      false
    )
  }

  override def tearDown(): Unit = {
    try {
      if (testInjection != null)
        scalaInjectionTestFixture.intelliLangConfig.replaceInjections(
          List.empty[BaseInjection].asJava,
          List(testInjection).asJava,
          false
        )
    } finally {
      super.tearDown()
    }
  }

  def testPatternInjection_CallArgument_RegularMethodCall(): Unit = {
    scalaInjectionTestFixture.doTest(
      RegexpLangId,
      s"""class A {
         |  def myMethod(pattern: String): Unit = ???
         |}
         |new A().myMethod("[0-9]+")
         |""".stripMargin,
      "[0-9]+"
    )
  }

  // SCL-24947: language injection should also work when the method is called with type arguments
  def testPatternInjection_CallArgument_GenericMethodCall(): Unit = {
    scalaInjectionTestFixture.doTest(
      RegexpLangId,
      s"""class A {
         |  def myMethod[T](pattern: String): T = ???
         |}
         |new A().myMethod[String]("[0-9]+")
         |""".stripMargin,
      "[0-9]+"
    )
  }
}
