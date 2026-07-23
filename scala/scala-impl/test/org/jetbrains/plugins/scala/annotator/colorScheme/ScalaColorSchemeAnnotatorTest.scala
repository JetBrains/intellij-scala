package org.jetbrains.plugins.scala.annotator.colorScheme

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaColorSchemeEditorHighlightingFixture
import org.jetbrains.plugins.scala.ScalaColorSchemeEditorHighlightingFixture.ExpectedHighlight
import org.jetbrains.plugins.scala.annotator.Message2
import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.RevertableChange.withModifiedSetting
import org.junit.Assert.assertEquals
import org.junit.Test

class ScalaColorSchemeAnnotatorTest extends ScalaColorSchemeAnnotatorTestBase[TextAttributesKey] {
  import org.jetbrains.plugins.scala.highlighter.DefaultHighlighter._

  private lazy val editorHighlightingFixture = new ScalaColorSchemeEditorHighlightingFixture(getFixture)

  private val caseClassDefinition =
    """case class MyCaseClass(field1: String, val field2: String, var field3: String)
      |                      (nonField: Int, val fieldSecondClause1: Int, var fieldSecondClause2: Int)
      |""".stripMargin

  private val classDefinition =
    """class MyClass(field1: String, val field2: String, var field3: String)
      |                      (nonField: Int, val fieldSecondClause1: Int, var fieldSecondClause2: Int)
      |""".stripMargin

  override protected def buildAnnotationsTestText(annotations: Seq[Message2]): String =
    annotations.map(_.textWithRangeAndCodeAttribute).mkString("\n")

  protected def needToAnnotateElement(element: PsiElement): Boolean = true

  override protected def getFilterByField(annotation: Message2): TextAttributesKey = annotation.textAttributesKey

  @Test
  def testAnnotateGeneratorAndEnumerator(): Unit = {
    val text =
      s"""for {
         |  case (a, b) <- Seq()
         |  (c, d) <- Seq()
         |} yield {
         |  println((a, b))
         |  println((c, d))
         |}
         |""".stripMargin

    testAnnotations(text, GENERATOR,
      """Info((14,15),a,Scala For statement value)
        |Info((17,18),b,Scala For statement value)
        |Info((32,33),c,Scala For statement value)
        |Info((35,36),d,Scala For statement value)
        |Info((68,69),a,Scala For statement value)
        |Info((71,72),b,Scala For statement value)
        |Info((86,87),c,Scala For statement value)
        |Info((89,90),d,Scala For statement value)
        |""".stripMargin)
  }

  @Test
  def testForYield(): Unit = {
    val text =
      s"""for {
         |  c <- Seq()
         |} yield {
         |}
         |""".stripMargin

    testAnnotations(text, GENERATOR,
      """
        |Info((8,9),c,Scala For statement value)
        |""".stripMargin)
  }

  @Test
  def testAnnotatePattern_1(): Unit = {
    val text =
      s"""??? match {
         |  case (a, b) =>
         |    (a, b)
         |}""".stripMargin

    testAnnotations(text, PATTERN,
      """Info((20,21),a,Scala Pattern value)
        |Info((23,24),b,Scala Pattern value)
        |Info((34,35),a,Scala Pattern value)
        |Info((37,38),b,Scala Pattern value)
        |""".stripMargin)
  }

  @Test
  def testAnnotatePattern_2(): Unit = {
    val text =
      s"""val sourceRoots = Seq()
         |val translatedTemplatePath = ""
         |
         |lazy val xxx: Option[String] = {
         |  sourceRoots collectFirst {
         |    case root if  root == null =>
         |      println(root)
         |      ???
         |  }
         |}
         |
         |lazy val yyy: Option[String] = {
         |  sourceRoots collectFirst {
         |    case root if root == null =>
         |      println(root)
         |      42 // type mismatch error
         |  }
         |}""".stripMargin

    testAnnotations(text, PATTERN,
      """Info((128,132),root,Scala Pattern value)
        |Info((137,141),root,Scala Pattern value)
        |Info((167,171),root,Scala Pattern value)
        |Info((261,265),root,Scala Pattern value)
        |Info((269,273),root,Scala Pattern value)
        |Info((299,303),root,Scala Pattern value)
        |""".stripMargin)
  }

  @Test
  def testAnnotatePattern_3(): Unit = {
    val text =
      s"""??? match {
         |  case a =>
         |}""".stripMargin

    testAnnotations(text, PATTERN,
      """Info((19,20),a,Scala Pattern value)
        |""".stripMargin)
  }

  @Test
  def testBooleans(): Unit = {
    val text =
      """
        |val t: Boolean = true
        |val f: Boolean = false
        |""".stripMargin

    testAnnotations(text, PREDEF,
      """Info((8,15),Boolean,Scala Predefined types)
        |Info((30,37),Boolean,Scala Predefined types)
        |""".stripMargin)
  }

  @Test
  def testStringInterpolation(): Unit = {
    testAllAnnotations(
      """raw"Hi ${System.currentTimeMillis()}"
        |""".stripMargin,
      """Info((9,15),System,Scala Object)
        |Info((16,33),currentTimeMillis,Scala Object method call)
        |""".stripMargin
    )
  }

  @Test
  def testStringInterpolation_2(): Unit = {
    getFixture.addFileToProject("defs.scala",
      """case class Bar()
        |def foo(b: Bar): Unit = ???
        |val bar = Bar()
        |""".stripMargin)

    testAllAnnotations(
      """s"one two ${foo(bar)} three"
        |""".stripMargin,
      """Info((12,15),foo,Scala Local method call)
        |Info((16,19),bar,Scala Local value)
        |""".stripMargin
    )
  }

  @Test
  def testLanguageInjection(): Unit = {
    val text =
      """
        |//language=Scala
        |val scalaText = "val a = 1"
        |""".stripMargin
    testAnnotations(text, LOCAL_VALUES,
      """Info((22,31),scalaText,Scala Local value)
        |""".stripMargin)
  }

  @Test
  def testSymbol(): Unit = {
    val text =
      """
        |val symbol = 'Symbol
        |""".stripMargin
    testAnnotations(text, LOCAL_VALUES,
      """Info((5,11),symbol,Scala Local value)
        |""".stripMargin)
  }

  @Test
  def testTypeAlias(): Unit = {
    val text =
      """
        |type A = String
        |""".stripMargin
    testAnnotations(text, TYPE_ALIAS,
      """Info((6,7),A,Scala Type Alias)
        |""".stripMargin)
  }

  @Test
  def testAbstractClass(): Unit = {
    val text =
      """
        |abstract class AbstractClass
        |""".stripMargin

    testAnnotations(text, ABSTRACT_CLASS,
      "Info((16,29),AbstractClass,Scala Abstract class)"
    )
  }

  @Test
  def testAnnotation(): Unit = {
    val text =
      """
        |@Source(url = "https://foo.com/")
        |trait Foo
        |""".stripMargin

    testAnnotations(text, ANNOTATION,
      """
        |Info((1,2),@,Scala Annotation name)
        |Info((2,8),Source,Scala Annotation name)
        |""".stripMargin
    )
  }

  @Test
  def testAnnotationAttributeColorSchemeKey(): Unit = {
    val text =
      """
        |@Source(url = "https://foo.com/")
        |trait Foo
        |""".stripMargin

    editorHighlightingFixture.assertHighlights(text, ExpectedHighlight("url", ANNOTATION_ATTRIBUTE))
  }

  @Test
  def testLazyValueColorSchemeKeys(): Unit = {
    val text =
      """class Template {
        |  lazy val templateLazy = 1
        |
        |  def method(): Unit = {
        |    lazy val localLazy = 2
        |  }
        |}
        |""".stripMargin

    editorHighlightingFixture.assertHighlights(
      text,
      ExpectedHighlight("templateLazy", LAZY),
      ExpectedHighlight("localLazy", LOCAL_LAZY)
    )
  }

  @Test
  def testScalaTestKeywordColorSchemeKey(): Unit = {
    addScalaFileToProject(
      "org/scalatest/Suite.scala",
      """package org.scalatest
        |
        |trait Suite {
        |  def test(name: String)(body: => Unit): Unit = ()
        |}
        |""".stripMargin
    )

    val setting = withModifiedSetting(ScalaProjectSettings.getInstance(getProject))(true)(
      _.isCustomScalatestSyntaxHighlighting,
      _.setCustomScalatestSyntaxHighlighting(_)
    )

    setting.run {
      val text =
        """class MySuite extends org.scalatest.Suite {
          |  test("highlighted") {}
          |}
          |""".stripMargin

      editorHighlightingFixture.assertHighlights(text, ExpectedHighlight("test", SCALATEST_KEYWORD, occurrence = 1))
    }
  }

  @Test
  def testAnonymousParameter(): Unit = {
    val text =
      """
        |(x: Int) => x
        |{ x: Int => x }
        |""".stripMargin

    testAnnotations(text, PARAMETER_OF_ANONIMOUS_FUNCTION,
      """Info((2,3),x,Scala Anonymous Parameter)
        |Info((13,14),x,Scala Anonymous Parameter)
        |Info((17,18),x,Scala Anonymous Parameter)
        |Info((27,28),x,Scala Anonymous Parameter)
        |""".stripMargin
    )
  }

  @Test
  def testMethodVsValueVsVariable(): Unit = {
    val text =
      """
        |def a = 0
        |val b = 1
        |var c = 2
        |""".stripMargin

    testAnnotations(text, METHOD_DECLARATION,
      """
        |Info((5,6),a,Scala Method declaration)
        |""".stripMargin
    )

    testAnnotations(text, LOCAL_VALUES,
      """
        |Info((15,16),b,Scala Local value)
        |""".stripMargin
    )

    testAnnotations(text, LOCAL_VARIABLES,
      """
        |Info((25,26),c,Scala Local variable)
        |""".stripMargin
    )
  }

  @Test
  def testCaseClassFieldDefinitions(): Unit =
    testAnnotations(caseClassDefinition, Set(CASE_CLASS_FIELD, CASE_CLASS_VAR_FIELD, VALUES, VARIABLES),
      """Info((23,29),field1,Scala Case class field)
        |Info((43,49),field2,Scala Case class field)
        |Info((63,69),field3,Scala Case class var field)
        |Info((121,139),fieldSecondClause1,Scala Template val)
        |Info((150,168),fieldSecondClause2,Scala Template var)""".stripMargin
    )

  @Test
  def testCaseClassFirstClauseFieldUsages(): Unit = {
    addCaseClassDefinition()
    val text =
      """val instanceCase: MyCaseClass = MyCaseClass("a", "b", "c")(42, 42, 42)
        |instanceCase.field1
        |instanceCase.field2
        |instanceCase.field3
        |""".stripMargin

    testAnnotations(text, Set(CASE_CLASS_FIELD, CASE_CLASS_VAR_FIELD),
      """Info((84,90),field1,Scala Case class field)
        |Info((104,110),field2,Scala Case class field)
        |Info((124,130),field3,Scala Case class var field)""".stripMargin
    )
  }

  @Test
  def testCaseClassSecondClauseFieldUsages(): Unit = {
    addCaseClassDefinition()
    val text =
      """val instanceCase: MyCaseClass = MyCaseClass("a", "b", "c")(42, 42, 42)
        |
        |//instanceCase.nonField
        |instanceCase.fieldSecondClause1
        |instanceCase.fieldSecondClause2
        |""".stripMargin

    testAnnotations(text, Set(VALUES, VARIABLES),
      """Info((109,127),fieldSecondClause1,Scala Template val)
        |Info((141,159),fieldSecondClause2,Scala Template var)""".stripMargin
    )
  }

  @Test
  def testClassFieldDefinitions(): Unit =
    testAnnotations(classDefinition, Set(VALUES, VARIABLES),
      """Info((34,40),field2,Scala Template val)
        |Info((54,60),field3,Scala Template var)
        |Info((112,130),fieldSecondClause1,Scala Template val)
        |Info((141,159),fieldSecondClause2,Scala Template var)""".stripMargin
    )

  @Test
  def testClassFirstClauseFieldUsages(): Unit = {
    addClassDefinition()
    val text =
      """val instance: MyClass = new MyClass("a", "b", "c")(42, 42, 42)
        |//instance.field1
        |instance.field2
        |instance.field3
        |""".stripMargin

    testAnnotations(text, Set(VALUES, VARIABLES),
      """Info((90,96),field2,Scala Template val)
        |Info((106,112),field3,Scala Template var)""".stripMargin
    )
  }

  @Test
  def testClassSecondClauseFieldUsages(): Unit = {
    addClassDefinition()
    val text =
      """val instance: MyClass = new MyClass("a", "b", "c")(42, 42, 42)
        |
        |//instance.nonField
        |instance.fieldSecondClause1
        |instance.fieldSecondClause2
        |""".stripMargin

    testAnnotations(text, Set(VALUES, VARIABLES),
      """Info((93,111),fieldSecondClause1,Scala Template val)
        |Info((121,139),fieldSecondClause2,Scala Template var)""".stripMargin
    )
  }

  @Test
  def testNonFieldConstructorParametersRemainParametersWithinClass(): Unit = {
    val text =
      """class MyClass(field1: String)(nonField: Int) {
        |  println(field1)
        |  println(nonField)
        |}
        |
        |case class MyCaseClass(field1: String)(nonField: Int) {
        |  println(nonField)
        |}
        |""".stripMargin

    testAnnotations(text, PARAMETER,
      """Info((14,20),field1,Scala Parameter)
        |Info((30,38),nonField,Scala Parameter)
        |Info((57,63),field1,Scala Parameter)
        |Info((75,83),nonField,Scala Parameter)
        |Info((127,135),nonField,Scala Parameter)
        |Info((154,162),nonField,Scala Parameter)""".stripMargin
    )
  }

  @Test
  def testTemplateFieldsKeepTemplateValueColor(): Unit = {
    val text =
      """class MyClass {
        |  val templateField: String = ""
        |}
        |
        |case class MyCaseClass(field1: String) {
        |  val templateField: String = ""
        |}
        |
        |object Usage {
        |  def use(): Unit = {
        |    val instance = new MyClass
        |    instance.templateField
        |    val instanceCase = MyCaseClass("a")
        |    instanceCase.templateField
        |  }
        |}
        |""".stripMargin

    testAnnotations(text, VALUES,
      """Info((22,35),templateField,Scala Template val)
        |Info((99,112),templateField,Scala Template val)
        |Info((210,223),templateField,Scala Template val)
        |Info((281,294),templateField,Scala Template val)""".stripMargin
    )
  }

  private def addCaseClassDefinition(): Unit =
    addScalaFileToProject("defs.scala", caseClassDefinition)

  private def addClassDefinition(): Unit =
    addScalaFileToProject("defs.scala", classDefinition)

  @Test
  def testCaseClassFieldColorSchemeEntriesUseTemplateFieldDefaults(): Unit = {
    assertEquals(VALUES, CASE_CLASS_FIELD.getFallbackAttributeKey)
    assertEquals(VARIABLES, CASE_CLASS_VAR_FIELD.getFallbackAttributeKey)
  }

  @Test
  def testHighlightParameterFieldAsParameterInScalaDoc(): Unit = {
    val text =
      """/**
        | * [[parameter1]]
        | * [[parameter2]]
        | * [[parameterFieldVal]]
        | * [[parameterFieldVar]]
        | *
        | * @param parameter1        description
        | * @param parameter2        description
        | * @param parameterFieldVal description
        | * @param parameterFieldVar description
        | */
        |class MyClass(
        |  parameter1: String, //NOTE USED outside constructor -> field IS NOT generated
        |  parameter2: String, //USED outside constructor -> field IS generated
        |  val parameterFieldVal: String,
        |  var parameterFieldVar: String
        |)
        |
        |/**
        | * [[parameterField]]
        | * [[parameterFieldVal]]
        | * [[parameterFieldVar]]
        | *
        | * @param parameterField    description
        | * @param parameterFieldVal description
        | * @param parameterFieldVar description
        | */
        |case class MyCaseClass(
        |  parameterField: String,
        |  val parameterFieldVal: String,
        |  var parameterFieldVar: String
        |)
        |""".stripMargin


    //adding more keys which I think could be accidentally used, but not too many to keep test data compact
    val keysOfInterest: Set[TextAttributesKey] = Set(
      DefaultHighlighter.VALUES,
      DefaultHighlighter.VARIABLES,
      DefaultHighlighter.CASE_CLASS_FIELD,
      DefaultHighlighter.CASE_CLASS_VAR_FIELD,
      DefaultHighlighter.LOCAL_VALUES,
      DefaultHighlighter.LOCAL_VARIABLES,
      DefaultHighlighter.PARAMETER,
      DefaultHighlighter.NAMED_ARGUMENT,
      DefaultHighlighter.PARAMETER_OF_ANONIMOUS_FUNCTION,
      DefaultHighlighter.TYPEPARAM,
      DefaultHighlighter.LOCAL_VALUES,
      DefaultHighlighter.LOCAL_VARIABLES,
      DefaultHighlighter.METHOD_DECLARATION,
      DefaultHighlighter.OBJECT_METHOD_CALL,
      DefaultHighlighter.LOCAL_METHOD_CALL,
      DefaultHighlighter.METHOD_CALL,
    )
    testAnnotations(text, keysOfInterest,
      """Info((9,19),parameter1,Scala Parameter)
        |Info((27,37),parameter2,Scala Parameter)
        |Info((45,62),parameterFieldVal,Scala Parameter)
        |Info((70,87),parameterFieldVar,Scala Parameter)
        |Info((103,113),parameter1,Scala Parameter)
        |Info((143,153),parameter2,Scala Parameter)
        |Info((183,200),parameterFieldVal,Scala Parameter)
        |Info((223,240),parameterFieldVar,Scala Parameter)
        |Info((274,284),parameter1,Scala Parameter)
        |Info((354,364),parameter2,Scala Parameter)
        |Info((429,446),parameterFieldVal,Scala Template val)
        |Info((462,479),parameterFieldVar,Scala Template var)
        |Info((500,514),parameterField,Scala Parameter)
        |Info((522,539),parameterFieldVal,Scala Parameter)
        |Info((547,564),parameterFieldVar,Scala Parameter)
        |Info((580,594),parameterField,Scala Parameter)
        |Info((620,637),parameterFieldVal,Scala Parameter)
        |Info((660,677),parameterFieldVar,Scala Parameter)
        |Info((720,734),parameterField,Scala Case class field)
        |Info((750,767),parameterFieldVal,Scala Case class field)
        |Info((783,800),parameterFieldVar,Scala Case class var field)""".stripMargin
    )
  }

  @Test
  def testNamedArguments(): Unit = {
    addScalaFileToProject("defs.scala",
      """class MyClass(param: Int, val paramFieldVal: Int, var paramFieldVar: Int)(param4: Int)
        |case class MyCaseClass(paramField: Int, val paramFieldVal: Int, var paramFieldVar: Int)(param4: Int)
        |def foo(param1: Int, param2: Int)(param3: Int): Unit = ???
        |val value = 42
        |""".stripMargin
    )
    val text =
      """new MyClass(param = 1, paramFieldVal = 2, paramFieldVar = 3)(param4 = 4)
        |MyCaseClass(paramField = 1, paramFieldVal = 2, paramFieldVar = 3)(param4 = 4)
        |foo(param1 = value, param2 = value)(param3 = value)
        |""".stripMargin

    //adding more keys which I think could be accidentally used, but not too many to keep test data compact
    val keysOfInterest: Set[TextAttributesKey] = Set(
      DefaultHighlighter.VALUES,
      DefaultHighlighter.LOCAL_VALUES,
      DefaultHighlighter.LOCAL_VARIABLES,
      DefaultHighlighter.VARIABLES,
      DefaultHighlighter.PARAMETER,
      DefaultHighlighter.NAMED_ARGUMENT,
      DefaultHighlighter.PARAMETER_OF_ANONIMOUS_FUNCTION,
      DefaultHighlighter.TYPEPARAM,
    )
    testAnnotations(
      text,
      keysOfInterest,
      """Info((12,19),param =,Scala Named Argument)
        |Info((23,38),paramFieldVal =,Scala Named Argument)
        |Info((42,57),paramFieldVar =,Scala Named Argument)
        |Info((61,69),param4 =,Scala Named Argument)
        |Info((85,97),paramField =,Scala Named Argument)
        |Info((101,116),paramFieldVal =,Scala Named Argument)
        |Info((120,135),paramFieldVar =,Scala Named Argument)
        |Info((139,147),param4 =,Scala Named Argument)
        |Info((155,163),param1 =,Scala Named Argument)
        |Info((164,169),value,Scala Local value)
        |Info((171,179),param2 =,Scala Named Argument)
        |Info((180,185),value,Scala Local value)
        |Info((187,195),param3 =,Scala Named Argument)
        |Info((196,201),value,Scala Local value)
        |""".stripMargin
    )
  }

  @Test
  def testUnderscoreLambdaWithAssignmentInParameterPosition(): Unit = {
    addScalaFileToProject("defs.scala",
      """class MyClass {
        |  var field: MyClass = ???
        |}
        |
        |def foo(set: MyClass => Unit): Unit = ???
        |""".stripMargin
    )

    //language=Scala
    val text =
      """foo(_.field = null)
        |foo(_.field.field.field = null)
        |""".stripMargin

    //adding more keys which I think could be accidentally used, but not too many to keep test data compact
    val keysOfInterest: Set[TextAttributesKey] = Set(
      DefaultHighlighter.VALUES,
      DefaultHighlighter.LOCAL_VALUES,
      DefaultHighlighter.LOCAL_VARIABLES,
      DefaultHighlighter.VARIABLES,
      DefaultHighlighter.PARAMETER,
      DefaultHighlighter.NAMED_ARGUMENT,
      DefaultHighlighter.PARAMETER_OF_ANONIMOUS_FUNCTION,
      DefaultHighlighter.TYPEPARAM,
    )
    testAnnotations(
      text,
      keysOfInterest,
      """Info((6,11),field,Scala Template var)
        |Info((26,31),field,Scala Template var)
        |Info((32,37),field,Scala Template var)
        |Info((38,43),field,Scala Template var)""".stripMargin
    )
  }

  @Test
  def testAssignmentToField(): Unit = {
    addScalaFileToProject("defs.scala",
      """class MyClass {
        |  var field: MyClass = ???
        |}
        |""".stripMargin
    )

    //language=Scala
    val text =
      """val myClass = new MyClass()
        |myClass.field = null
        |val x: MyClass => Unit = _.field.field.field = null
        |""".stripMargin

    //adding more keys which I think could be accidentally used, but not too many to keep test data compact
    val keysOfInterest: Set[TextAttributesKey] = Set(
      DefaultHighlighter.VALUES,
      DefaultHighlighter.LOCAL_VALUES,
      DefaultHighlighter.LOCAL_VARIABLES,
      DefaultHighlighter.VARIABLES,
      DefaultHighlighter.PARAMETER,
      DefaultHighlighter.NAMED_ARGUMENT,
      DefaultHighlighter.PARAMETER_OF_ANONIMOUS_FUNCTION,
      DefaultHighlighter.TYPEPARAM,
    )
    testAnnotations(
      text,
      keysOfInterest,
      """Info((4,11),myClass,Scala Local value)
        |Info((28,35),myClass,Scala Local value)
        |Info((36,41),field,Scala Template var)
        |Info((53,54),x,Scala Local value)
        |Info((76,81),field,Scala Template var)
        |Info((82,87),field,Scala Template var)
        |Info((88,93),field,Scala Template var)""".stripMargin
    )
  }

  @Test
  def testAbstractVariables(): Unit = {
    testAnnotations(
      //language=Scala
      """class Foo {
        |  val x: Int
        |  var y: Int
        |}
        |""".stripMargin,
      Set(
        DefaultHighlighter.VALUES,
        DefaultHighlighter.VARIABLES,
        DefaultHighlighter.LOCAL_VALUES,
        DefaultHighlighter.LOCAL_VARIABLES,
      ),
      """Info((18,19),x,Scala Template val)
        |Info((31,32),y,Scala Template var)""".stripMargin
    )
  }

  // SCL-25122
  @Test
  def testScalaDocRefLinkDotHighlighting(): Unit = {
    val text =
      """/**
        | * [[Target.func]]
        | * [[Target#func]]
        | * [[#blub]]
        | * [[this.blub]]
        | * [[package.SomeClass]]
        | */
        |""".stripMargin

    testAnnotations(text, Set(DOT, KEYWORD),
      """Info((15,16),.,Scala Dot)
        |Info((34,35),#,Scala Dot)
        |Info((47,48),#,Scala Dot)
        |Info((60,64),this,Scala Keyword)
        |Info((64,65),.,Scala Dot)
        |Info((77,84),package,Scala Keyword)
        |Info((84,85),.,Scala Dot)""".stripMargin)
  }

}
