package org.jetbrains.plugins.scala.intelliLang.injection

import com.intellij.patterns.compiler.PatternCompilerImpl.LazyPresentablePattern
import org.intellij.lang.annotations.Language
import org.intellij.plugins.intelliLang.inject.config.{BaseInjection, InjectionPlace}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.intelliLang.injection.InjectionTestUtils.*
import org.junit.Assert.*

import scala.jdk.CollectionConverters.*

abstract class ScalaLanguageInjectorTestBase extends InjectionInBodyTestBase {

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

  def testAnnotationInjection_InClassConstructor_WithNamedArgument(): Unit = {
    doAnnotationTestInBody(
      ScalaLangId,
      s"""class MyClass(
         |  @Language("Scala") param: String
         |)
         |
         |new MyClass(param = "${CARET}val x = 0")
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

  def testAnnotationInjection_InClassSecondaryConstructor_WithNamedArguments(): Unit = {
    doAnnotationTestInBody(
      ScalaLangId,
      s"""class MyClass(
         |  @Language("Scala") param: String
         |) {
         |  def this(@Language("Scala") param: String, x: Int) = this(param)
         |}
         |
         |new MyClass(param = "${CARET}val x = 0", x = 42)
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

  def testAnnotationInjection_InCaseClassConstructor_CalledViaNew_WithNamedArgument(): Unit = {
    doAnnotationTestInBody(
      ScalaLangId,
      s"""case class MyCaseClass(
         |  @Language("Scala") param: String
         |)
         |
         |new MyCaseClass(param = "${CARET}val x = 0")
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

  def testAnnotationInjection_InCaseClassSecondaryConstructor_CalledViaNew_WithNamedArguments(): Unit = {
    doAnnotationTestInBody(
      ScalaLangId,
      s"""case class MyCaseClass(
         |  @Language("Scala") param: String
         |) {
         |  def this(@Language("Scala") param: String, x: Int) = this(param)
         |}
         |
         |new MyCaseClass(param = "${CARET}val x = 0", x = 42)
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

  def testAnnotationInjection_InCaseClassConstructor_CalledViaApply_WithNamedArgument(): Unit = {
    doAnnotationTestInBody(
      ScalaLangId,
      s"""case class MyCaseClass(
         |  @Language("Scala") param: String
         |)
         |
         |MyCaseClass(param = "${CARET}val x = 0")
         |""".stripMargin,
      "val x = 0"
    )
  }

  def testAnnotationInjection_InCaseClassCopy_WithNamedArgument(): Unit = {
    doAnnotationTestInBody(
      ScalaLangId,
      s"""case class MyCaseClass(
         |  @Language("Scala") param: String,
         |  x: Int = 42
         |)
         |
         |val myCaseClass = MyCaseClass(param = "val y = 0")
         |myCaseClass.copy(param = "${CARET}val x = 0")
         |""".stripMargin,
      "val x = 0"
    )
  }

  def testAnnotationInjection_InImplicitClassConstructor_CalledWithoutNew_WithNamedArgument(): Unit = {
    doAnnotationTestInBody(
      ScalaLangId,
      s"""implicit class MyImplicitClass(
         |  @Language("Scala") param: String
         |)
         |
         |MyImplicitClass(param = "${CARET}val x = 0")
         |""".stripMargin,
      "val x = 0"
    )
  }

  ////////////////////////////////////////
  // SCL-24959 example lines coverage
  ////////////////////////////////////////

  //SCL-24959C
  @Language("Scala")
  private val Scl24959ClassesBody =
    """class SQLString1(@Language("Scala") sql: String)
      |class SQLString2(@Language("Scala") val sql: String)
      |class SQLString3(@Language("Scala") private val sql: String)
      |
      |implicit class SQLString4(@Language("Scala") sql: String)
      |implicit class SQLString5(@Language("Scala") val sql: String)
      |implicit class SQLString6(@Language("Scala") private val sql: String)
      |
      |implicit class SQLString7(@Language("Scala") sql: String) extends AnyVal
      |implicit class SQLString8(@Language("Scala") val sql: String) extends AnyVal
      |implicit class SQLString9(@Language("Scala") private val sql: String) extends AnyVal
      |""".stripMargin

  //
  // NOTE: This SCL-24959 block provides indirect end-to-end coverage for:
  // `org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.parameterForSyntheticParameter`
  // through language injection call paths (alongside other logic).
  //
  // Direct branch-level coverage belongs to:
  // `org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtilTest`.
  //
  // These tests are complementary and intentionally stay focused on language injection behavior.
  //
  protected final def doScl24959InvocationTest(@Language("Scala") code: String): Unit = {
    myFixture.addFileToProject("definitions.scala", Scl24959ClassesBody)

    doAnnotationTestInBody(
      ScalaLangId,
      code,
      "val x = 0"
    )
  }

  def testAnnotationInjection_SCL24959_New_SQLString1_Param(): Unit =
    doScl24959InvocationTest("""new SQLString1("val x = 0")""")

  def testAnnotationInjection_SCL24959_New_SQLString2_ValParam(): Unit =
    doScl24959InvocationTest("""new SQLString2("val x = 0")""")

  def testAnnotationInjection_SCL24959_New_SQLString3_PrivateValParam(): Unit =
    doScl24959InvocationTest("""new SQLString3("val x = 0")""")

  def testAnnotationInjection_SCL24959_New_SQLString4_ImplicitClass_Param(): Unit =
    doScl24959InvocationTest("""new SQLString4("val x = 0")""")

  def testAnnotationInjection_SCL24959_New_SQLString5_ImplicitClass_ValParam(): Unit =
    doScl24959InvocationTest("""new SQLString5("val x = 0")""")

  def testAnnotationInjection_SCL24959_New_SQLString6_ImplicitClass_PrivateValParam(): Unit =
    doScl24959InvocationTest("""new SQLString6("val x = 0")""")

  def testAnnotationInjection_SCL24959_New_SQLString7_ImplicitValClass_Param(): Unit =
    doScl24959InvocationTest("""new SQLString7("val x = 0")""")

  def testAnnotationInjection_SCL24959_New_SQLString8_ImplicitValClass_ValParam(): Unit =
    doScl24959InvocationTest("""new SQLString8("val x = 0")""")

  def testAnnotationInjection_SCL24959_New_SQLString9_ImplicitValClass_PrivateValParam(): Unit =
    doScl24959InvocationTest("""new SQLString9("val x = 0")""")

  def testAnnotationInjection_SCL24959_WithoutNew_SQLString4_ImplicitClass_Param(): Unit =
    doScl24959InvocationTest("""SQLString4("val x = 0")""")

  def testAnnotationInjection_SCL24959_WithoutNew_SQLString5_ImplicitClass_ValParam(): Unit =
    doScl24959InvocationTest("""SQLString5("val x = 0")""")

  def testAnnotationInjection_SCL24959_WithoutNew_SQLString6_ImplicitClass_PrivateValParam(): Unit =
    doScl24959InvocationTest("""SQLString6("val x = 0")""")

  def testAnnotationInjection_SCL24959_WithoutNew_SQLString7_ImplicitValClass_Param(): Unit =
    doScl24959InvocationTest("""SQLString7("val x = 0")""")

  def testAnnotationInjection_SCL24959_WithoutNew_SQLString8_ImplicitValClass_ValParam(): Unit =
    doScl24959InvocationTest("""SQLString8("val x = 0")""")

  def testAnnotationInjection_SCL24959_WithoutNew_SQLString9ImplicitValClass_PrivateValParam(): Unit =
    doScl24959InvocationTest("""SQLString9("val x = 0")""")

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

class ScalaLanguageInjectorTest_Scala2 extends ScalaLanguageInjectorTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13
}

class ScalaLanguageInjectorTest_Scala3 extends ScalaLanguageInjectorTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3

  def testAnnotationInjection_SCL24959_CalledViaUniversalApply_SQLString1_Class_Param(): Unit =
    doScl24959InvocationTest("""SQLString1("val x = 0")""")

  def testAnnotationInjection_SCL24959_CalledViaUniversalApply_SQLString2_Class_ValParam(): Unit =
    doScl24959InvocationTest("""SQLString2("val x = 0")""")

  def testAnnotationInjection_SCL24959_CalledViaUniversalApply_SQLString3_Class_PrivateValParam(): Unit =
    doScl24959InvocationTest("""SQLString3("val x = 0")""")

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

  def testAnnotationInjection_InClassConstructor_CalledViaUniversalApply_WithNamedArgument(): Unit = {
    doAnnotationTestInBody(
      ScalaLangId,
      s"""class MyClass(
         |  @Language("Scala") param: String
         |)
         |
         |MyClass(param = "${CARET}val x = 0")
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

  def testAnnotationInjection_InClassSecondaryConstructor_CalledViaUniversalApply_WithNamedArguments(): Unit = {
    doAnnotationTestInBody(
      ScalaLangId,
      s"""class MyClass(
         |  @Language("Scala") param: String
         |) {
         |  def this(@Language("Scala") param: String, x: Int) = this(param)
         |}
         |
         |MyClass(param = "${CARET}val x = 0", x = 42)
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
