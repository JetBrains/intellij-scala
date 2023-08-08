package org.jetbrains.plugins.scala.lang.overrideImplement

import com.intellij.openapi.project.Project
import com.intellij.testFramework.EditorTestUtil.{CARET_TAG, SELECTION_END_TAG, SELECTION_START_TAG}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.overrideImplement.ScExtensionMethodMember
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Latest))
class ScalaOverrideImplementTest_3_Latest extends ScalaOverrideImplementTestBase {
  override protected def prepareSettings(newSettings: ScalaCodeStyleSettings)(implicit project: Project) = {
    val oldSettings = super.prepareSettings(newSettings)
    ScalaCodeStyleSettings.getInstance(project).USE_SCALA3_INDENTATION_BASED_SYNTAX = newSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX
    oldSettings
  }

  override protected def rollbackSettings(oldSettings: ScalaCodeStyleSettings)(implicit project: Project): Unit = {
    ScalaCodeStyleSettings.getInstance(project).USE_SCALA3_INDENTATION_BASED_SYNTAX = oldSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX
    super.rollbackSettings(oldSettings)
  }

  private def defaultSettings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject))

  private def settingsWithIndentationBasedSyntax: ScalaCodeStyleSettings = {
    val settings = defaultSettings
    settings.USE_SCALA3_INDENTATION_BASED_SYNTAX = true
    settings
  }

  private def settingsWithoutIndentationBasedSyntax: ScalaCodeStyleSettings = {
    val settings = defaultSettings
    settings.USE_SCALA3_INDENTATION_BASED_SYNTAX = false
    settings
  }

  def testImplementUsingIndentationBasedSyntaxWhenSettingIsOn(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |class B${CARET_TAG}ar extends Foo
         |
         |""".stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |class Bar extends Foo:
         |  override def foo(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |
         |""".stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithIndentationBasedSyntax)
  }

  def test_SCL_19753_ImplementUsingIndentationBasedSyntaxWhenSettingIsOn(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |class B${CARET_TAG}ar extends Foo:
         |
         |""".stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |class Bar extends Foo:
         |  override def foo(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |
         |""".stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithIndentationBasedSyntax)
  }

  def test_SCL_19753_ImplementUsingIndentationBasedSyntaxWhenSettingIsOff(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |class B${CARET_TAG}ar extends Foo:
         |
         |""".stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |class Bar extends Foo:
         |  override def foo(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |
         |""".stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithoutIndentationBasedSyntax)
  }

  def testImplementUsingIndentationBasedSyntax_InsideAnEmptyTemplateBody(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |class Bar extends Foo:
         |  $CARET_TAG
         |""".stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |class Bar extends Foo:
         |  override def foo(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |  ${""}
         |""".stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithIndentationBasedSyntax)
  }

  def testImplementUsingIndentationBasedSyntax_InsideAnEmptyTemplateBody_CaretUnindented_ButStillSomeCodeIsExpectedInTemplateBody(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |class Bar extends Foo:
         |$CARET_TAG
         |""".stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |class Bar extends Foo:
         |  override def foo(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |
         |""".stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithIndentationBasedSyntax)
  }

  def testImplementUsingIndentationBasedSyntax_InsertInTheBeginningOfTemplateBody(): Unit = {
    val fileText =
      s"""package test
         |
         |trait Foo:
         |  def foo1(x: Int): String
         |  def foo2(x: Int): String
         |  def foo3(x: Int): String
         |
         |class Bar extends Foo:
         |  $CARET_TAG
         |  override def foo1(x: Int): String = ???
         |
         |  override def foo2(x: Int): String = ???
         |""".stripMargin
    val expectedText =
      s"""package test
         |
         |trait Foo:
         |  def foo1(x: Int): String
         |  def foo2(x: Int): String
         |  def foo3(x: Int): String
         |
         |class Bar extends Foo:
         |  override def foo3(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |
         |  override def foo1(x: Int): String = ???
         |
         |  override def foo2(x: Int): String = ???
         |""".stripMargin
    val methodName: String = "foo3"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithIndentationBasedSyntax)
  }

  def testImplementUsingIndentationBasedSyntax_InsertInTheMiddleOfTemplateBody(): Unit = {
    val fileText =
      s"""package test
         |
         |trait Foo:
         |  def foo1(x: Int): String
         |  def foo2(x: Int): String
         |  def foo3(x: Int): String
         |
         |class Bar extends Foo:
         |  override def foo1(x: Int): String = ???
         |  $CARET_TAG
         |  override def foo2(x: Int): String = ???
         |""".stripMargin
    val expectedText =
      s"""package test
         |
         |trait Foo:
         |  def foo1(x: Int): String
         |  def foo2(x: Int): String
         |  def foo3(x: Int): String
         |
         |class Bar extends Foo:
         |  override def foo1(x: Int): String = ???
         |
         |  override def foo3(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |
         |  override def foo2(x: Int): String = ???
         |""".stripMargin
    val methodName: String = "foo3"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithIndentationBasedSyntax)
  }

  def testImplementUsingIndentationBasedSyntax_InsertInTheEndOfTemplateBody(): Unit = {
    val fileText =
      s"""package test
         |
         |trait Foo:
         |  def foo1(x: Int): String
         |  def foo2(x: Int): String
         |  def foo3(x: Int): String
         |
         |class Bar extends Foo:
         |  override def foo1(x: Int): String = ???
         |
         |  override def foo2(x: Int): String = ???
         |  $CARET_TAG
         |""".stripMargin
    val expectedText =
      s"""package test
         |
         |trait Foo:
         |  def foo1(x: Int): String
         |  def foo2(x: Int): String
         |  def foo3(x: Int): String
         |
         |class Bar extends Foo:
         |  override def foo1(x: Int): String = ???
         |
         |  override def foo2(x: Int): String = ???
         |
         |  override def foo3(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |  ${""}
         |""".stripMargin
    val methodName: String = "foo3"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithIndentationBasedSyntax)
  }

  def testImplementUsingIndentationBasedSyntax_InsertInTheEndOfTemplateBody_WithEndMarker(): Unit = {
    val fileText =
      s"""package test
         |
         |trait Foo:
         |  def foo1(x: Int): String
         |  def foo2(x: Int): String
         |  def foo3(x: Int): String
         |
         |class Bar extends Foo:
         |  override def foo1(x: Int): String = ???
         |
         |  override def foo2(x: Int): String = ???
         |  $CARET_TAG
         |end Bar
         |""".stripMargin
    val expectedText =
      s"""package test
         |
         |trait Foo:
         |  def foo1(x: Int): String
         |  def foo2(x: Int): String
         |  def foo3(x: Int): String
         |
         |class Bar extends Foo:
         |  override def foo1(x: Int): String = ???
         |
         |  override def foo2(x: Int): String = ???
         |
         |  override def foo3(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |end Bar
         |""".stripMargin
    val methodName: String = "foo3"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithIndentationBasedSyntax)
  }

  def testImplementUsingIndentationBasedSyntax_ChooseCorrectIndentationLevel_Level2_1(): Unit = {
    val fileText =
      s"""trait MyTrait1 { def fooFromTrait1: String }
         |trait MyTrait2 { def fooFromTrait2: String }
         |
         |object wrapper0:
         |  object wrapper1 extends MyTrait1:
         |    println()
         |    object wrapper2 extends MyTrait2:
         |      println()
         |        $CARET""".stripMargin
    assertTemplateDefinitionSelectedForAction(fileText, "wrapper2")
  }

  def testImplementUsingIndentationBasedSyntax_ChooseCorrectIndentationLevel_Level2_2(): Unit = {
    val fileText =
      s"""trait MyTrait1 { def fooFromTrait1: String }
         |trait MyTrait2 { def fooFromTrait2: String }
         |
         |object wrapper0:
         |  object wrapper1 extends MyTrait1:
         |    println()
         |    object wrapper2 extends MyTrait2:
         |      println()
         |      $CARET""".stripMargin
    assertTemplateDefinitionSelectedForAction(fileText, "wrapper2")
  }

  def testImplementUsingIndentationBasedSyntax_ChooseCorrectIndentationLevel_Level1_1(): Unit = {
    val fileText =
      s"""trait MyTrait1 { def fooFromTrait1: String }
         |trait MyTrait2 { def fooFromTrait2: String }
         |
         |object wrapper0:
         |  object wrapper1 extends MyTrait1:
         |    println()
         |    object wrapper2 extends MyTrait2:
         |      println()
         |     $CARET""".stripMargin
    assertTemplateDefinitionSelectedForAction(fileText, "wrapper1")
  }

  def testImplementUsingIndentationBasedSyntax_ChooseCorrectIndentationLevel_Level1_2(): Unit = {
    val fileText =
      s"""trait MyTrait1 { def fooFromTrait1: String }
         |trait MyTrait2 { def fooFromTrait2: String }
         |
         |object wrapper0:
         |  object wrapper1 extends MyTrait1:
         |    println()
         |    object wrapper2 extends MyTrait2:
         |      println()
         |    $CARET""".stripMargin
    assertTemplateDefinitionSelectedForAction(fileText, "wrapper1")
  }

  def testImplementUsingIndentationBasedSyntax_ChooseCorrectIndentationLevel_Level0_1(): Unit = {
    val fileText =
      s"""trait MyTrait1 { def fooFromTrait1: String }
         |trait MyTrait2 { def fooFromTrait2: String }
         |
         |object wrapper0:
         |  object wrapper1 extends MyTrait1:
         |    println()
         |    object wrapper2 extends MyTrait2:
         |      println()
         |   $CARET""".stripMargin
    assertTemplateDefinitionSelectedForAction(fileText, "wrapper0")
  }

  def testImplementUsingIndentationBasedSyntax_ChooseCorrectIndentationLevel_Level0_2(): Unit = {
    val fileText =
      s"""trait MyTrait1 { def fooFromTrait1: String }
         |trait MyTrait2 { def fooFromTrait2: String }
         |
         |object wrapper0:
         |  object wrapper1 extends MyTrait1:
         |    println()
         |    object wrapper2 extends MyTrait2:
         |      println()
         |  $CARET""".stripMargin
    assertTemplateDefinitionSelectedForAction(fileText, "wrapper0")
  }

  def testImplementUsingIndentationBasedSyntax_ChooseCorrectIndentationLevel_Level_NotChosen(): Unit = {
    val fileText =
      s"""trait MyTrait1 { def fooFromTrait1: String }
         |trait MyTrait2 { def fooFromTrait2: String }
         |
         |object wrapper0:
         |  object wrapper1 extends MyTrait1:
         |    println()
         |    object wrapper2 extends MyTrait2:
         |      println()
         |$CARET""".stripMargin
    assertTemplateDefinitionSelectedForAction(fileText, null)
  }

  def testImplementUsingBracesSyntax_IndentationShouldNotMatter_StartOfTheBlock(): Unit = {
    val fileText =
      s"""trait MyTrait1 { def fooFromTrait1: String }
         |trait MyTrait2 { def fooFromTrait2: String }
         |
         |object wrapper0:
         |  object wrapper1 extends MyTrait1:
         |    println()
         |    object wrapper2 extends MyTrait2 {
         |    $CARET
         |      println()
         |      println()
         |    }
         |""".stripMargin
    assertTemplateDefinitionSelectedForAction(fileText, "wrapper2")
  }

  def testImplementUsingBracesSyntax_IndentationShouldNotMatter_MiddleOfTheBlock(): Unit = {
    val fileText =
      s"""trait MyTrait1 { def fooFromTrait1: String }
         |trait MyTrait2 { def fooFromTrait2: String }
         |
         |object wrapper0:
         |  object wrapper1 extends MyTrait1:
         |    println()
         |    object wrapper2 extends MyTrait2 {
         |      println()
         |    $CARET
         |      println()
         |    }
         |""".stripMargin
    assertTemplateDefinitionSelectedForAction(fileText, "wrapper2")
  }

  def testImplementUsingBracesSyntax_IndentationShouldNotMatter_EndOfTheBlock(): Unit = {
    val fileText =
      s"""trait MyTrait1 { def fooFromTrait1: String }
         |trait MyTrait2 { def fooFromTrait2: String }
         |
         |object wrapper0:
         |  object wrapper1 extends MyTrait1:
         |    println()
         |    object wrapper2 extends MyTrait2 {
         |      println()
         |      println()
         |    $CARET
         |    }
         |""".stripMargin
    assertTemplateDefinitionSelectedForAction(fileText, "wrapper2")
  }

  def testImplementUsingStandardSyntaxWithBracesWhenSettingIsOff(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |class B${CARET_TAG}ar extends Foo
         |""".stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |class Bar extends Foo {
         |  override def foo(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |}
         |""".stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithoutIndentationBasedSyntax)
  }

  def testImplementWithUsingParameters(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |trait Context
         |trait Foo:
         |  def foo(x: Int)(using String)(y: Double)(using ctx: Context): String
         |
         |class Bar extends Foo:
         |  $CARET_TAG
         |""".stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait Context
         |trait Foo:
         |  def foo(x: Int)(using String)(y: Double)(using ctx: Context): String
         |
         |class Bar extends Foo:
         |  override def foo(x: Int)(using String)(y: Double)(using ctx: Context): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |  ${""}
         |""".stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithIndentationBasedSyntax)
  }

  def testOverrideWithUsingParameters(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |trait Context
         |class Foo:
         |  def foo(x: Int)(using String)(y: Double)(using ctx: Context): String = summon[String]
         |
         |class Bar extends Foo:
         |  $CARET_TAG
         |""".stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait Context
         |class Foo:
         |  def foo(x: Int)(using String)(y: Double)(using ctx: Context): String = summon[String]
         |
         |class Bar extends Foo:
         |  override def foo(x: Int)(using String)(y: Double)(using ctx: Context): String = ${SELECTION_START_TAG}super.foo(x)(y)$SELECTION_END_TAG
         |  ${""}
         |""".stripMargin
    val methodName: String = "foo"
    val isImplement = false
    runTest(methodName, fileText, expectedText, isImplement, settingsWithIndentationBasedSyntax)
  }

  def test_SCL_20350_ImplementUsingIndentationBasedSyntaxInsideAnEmptyGivenTemplateBody_Inner(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |object Container {
         |  given Foo w${CARET_TAG}ith
         |}
         |""".stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |object Container {
         |  given Foo with
         |    override def foo(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |}
         |""".stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithIndentationBasedSyntax)
  }

  def test_SCL_20350_ImplementUsingIndentationBasedSyntaxInsideAnEmptyGivenTemplateBody_TopLevel(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |given Foo w${CARET_TAG}ith
         |""".stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |given Foo with
         |  override def foo(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |""".stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithIndentationBasedSyntax)
  }

  def test_SCL_20350_ImplementUsingStandardSyntaxInsideAnEmptyGivenTemplateBody_TopLevel(): Unit = {
    val fileText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |given Foo w${CARET_TAG}ith
         |""".stripMargin
    val expectedText =
      s"""
         |package test
         |
         |trait Foo:
         |  def foo(x: Int): String
         |
         |given Foo with {
         |  override def foo(x: Int): String = $SELECTION_START_TAG???$SELECTION_END_TAG
         |}
         |""".stripMargin
    val methodName: String = "foo"
    val isImplement = true
    runTest(methodName, fileText, expectedText, isImplement, settingsWithoutIndentationBasedSyntax)
  }

  private def addHelperClassesForExtensionTests(): Unit = {
    getFixture.addFileToProject(
      "helpers.scala",
      """trait MyTrait
        |class MyClass extends MyTrait
        |case class MyCaseClass[T]()
        |class MyContext""".stripMargin
    )
  }

  def testExtension_SimpleSingle_Override(): Unit = {
    val before =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    def myExtSimpleSingleNonAbstract: String = ???
         |
         |class ${CARET}MyChildClass extends MyBaseClass
         |""".stripMargin
    val after =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    def myExtSimpleSingleNonAbstract: String = ???
         |
         |class MyChildClass extends MyBaseClass:
         |  extension (target: String)
         |    override def myExtSimpleSingleNonAbstract: String = ${START}super.myExtSimpleSingleNonAbstract(target)$END
         |""".stripMargin
    runTest("myExtSimpleSingleNonAbstract", before, after, isImplement = false)
  }

  def testExtension_SimpleSingleWithParams_Override(): Unit = {
    val before =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    def myExtSimpleSingleNonAbstract(param: Int): String = ???
         |
         |class ${CARET}MyChildClass extends MyBaseClass
         |""".stripMargin
    val after =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    def myExtSimpleSingleNonAbstract(param: Int): String = ???
         |
         |class MyChildClass extends MyBaseClass:
         |  extension (target: String)
         |    override def myExtSimpleSingleNonAbstract(param: Int): String = ${START}super.myExtSimpleSingleNonAbstract(target)(param)$END
         |""".stripMargin
    runTest("myExtSimpleSingleNonAbstract", before, after, isImplement = false)
  }

  def testExtension_SimpleSingle(): Unit = {
    val before =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    def myExtSimpleSingleAbstract: String
         |
         |class ${CARET}MyChildClass extends MyBaseClass
         |""".stripMargin
    val after =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    def myExtSimpleSingleAbstract: String
         |
         |class MyChildClass extends MyBaseClass:
         |  extension (target: String)
         |    override def myExtSimpleSingleAbstract: String = ${START}???$END
         |""".stripMargin
    runTest("myExtSimpleSingleAbstract", before, after, isImplement = true)
  }

  def testExtension_MultipleMethods(): Unit = {
    val before =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    def myExt21: String
         |    def myExt22(p: Int): String
         |
         |class ${CARET}MyChildClass extends MyBaseClass
         |""".stripMargin
    val after =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    def myExt21: String
         |    def myExt22(p: Int): String
         |
         |class MyChildClass extends MyBaseClass:
         |  extension (target: String)
         |    override def myExt21: String = ???
         |    override def myExt22(p: Int): String = ???
         |""".stripMargin

    runImplementAllTest(before, after)
  }

  def testExtension_MultipleMethods_UseBraces(): Unit = {
    val before =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    def myExt21: String
         |    def myExt22(p: Int): String
         |
         |class ${CARET}MyChildClass extends MyBaseClass
         |""".stripMargin
    val after =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    def myExt21: String
         |    def myExt22(p: Int): String
         |
         |class MyChildClass extends MyBaseClass {
         |  extension (target: String) {
         |    override def myExt21: String = ???
         |    override def myExt22(p: Int): String = ???
         |  }
         |}
         |""".stripMargin

    ScalaCodeStyleSettings.getInstance(getProject).USE_SCALA3_INDENTATION_BASED_SYNTAX = false
    runImplementAllTest(before, after)
  }

  def testExtension_WithTypeParameters(): Unit = {
    val before =
      s"""abstract class MyBaseClass[T1, T2 <: CharSequence]:
         |  extension [E1](t: E1)
         |    def myExt1[F1, F2 <: T1, F3 <: T2]: String
         |
         |  extension [E1 <: T1](t: E1)
         |    def myExt2: String
         |
         |  extension [E1 <: T2](t: E1)
         |    def myExt3: String
         |
         |class ${CARET}MyChildClass[ChildClassTypeParam1] extends MyBaseClass[ChildClassTypeParam1, String]
         |""".stripMargin
    val after =
      s"""abstract class MyBaseClass[T1, T2 <: CharSequence]:
         |  extension [E1](t: E1)
         |    def myExt1[F1, F2 <: T1, F3 <: T2]: String
         |
         |  extension [E1 <: T1](t: E1)
         |    def myExt2: String
         |
         |  extension [E1 <: T2](t: E1)
         |    def myExt3: String
         |
         |class MyChildClass[ChildClassTypeParam1] extends MyBaseClass[ChildClassTypeParam1, String]:
         |  extension [E1](t: E1)
         |    override def myExt1[F1, F2 <: ChildClassTypeParam1, F3 <: String]: String = ???
         |  extension [E1 <: ChildClassTypeParam1](t: E1)
         |    override def myExt2: String = ???
         |  extension [E1 <: String](t: E1)
         |    override def myExt3: String = ???
         |""".stripMargin

    runImplementAllTest(before, after)
  }


  def testExtension_WithModifier(): Unit = {
    val before =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    def myExt41: String
         |    protected def myExt42(p: Int): String
         |
         |class ${CARET}MyChildClass extends MyBaseClass
         |""".stripMargin
    val after =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    def myExt41: String
         |    protected def myExt42(p: Int): String
         |
         |class MyChildClass extends MyBaseClass:
         |  extension (target: String)
         |    override def myExt41: String = ???
         |    override protected def myExt42(p: Int): String = ???
         |""".stripMargin

    runImplementAllTest(before, after)
  }

  def testExtension_WithSoftModifiers(): Unit = {
    val before =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    inline def myExt51: String
         |    transparent inline def myExt52(p: Int): String
         |
         |class ${CARET}MyChildClass extends MyBaseClass
         |""".stripMargin
    val after =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    inline def myExt51: String
         |    transparent inline def myExt52(p: Int): String
         |
         |class MyChildClass extends MyBaseClass:
         |  extension (target: String)
         |    override inline def myExt51: String = ???
         |    override transparent inline def myExt52(p: Int): String = ???
         |""".stripMargin

    runImplementAllTest(before, after)
  }


  def testExtension_CopyDocs(): Unit = {
    val before =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    /** Doc for myExt1 */
         |    def myExt61: String
         |    /** Doc for myExt2 */
         |    def myExt62: String
         |
         |class ${CARET}MyChildClass extends MyBaseClass
         |""".stripMargin
    val after =
      s"""abstract class MyBaseClass:
         |  extension (target: String)
         |    /** Doc for myExt1 */
         |    def myExt61: String
         |    /** Doc for myExt2 */
         |    def myExt62: String
         |
         |class MyChildClass extends MyBaseClass:
         |  extension (target: String)
         |    /** Doc for myExt1 */
         |    override def myExt61: String = ???
         |    /** Doc for myExt2 */
         |    override def myExt62: String = ???
         |""".stripMargin

    runImplementAllTest(before, after, copyScalaDoc = true)
  }

  def testExtension_WithUsingClauses(): Unit = {
    val before =
      s"""abstract class MyBaseClass:
         |  extension (target: Int)(using MyContext, Long)(using mt: MyCaseClass[_], cs: CharSequence)
         |    def myExt7(p1: Int)(using u1: Short, u2: MyTrait)(p2: Long): String
         |
         |class ${CARET}MyChildClass extends MyBaseClass
         |""".stripMargin
    val after =
      s"""abstract class MyBaseClass:
         |  extension (target: Int)(using MyContext, Long)(using mt: MyCaseClass[_], cs: CharSequence)
         |    def myExt7(p1: Int)(using u1: Short, u2: MyTrait)(p2: Long): String
         |
         |class MyChildClass extends MyBaseClass:
         |  extension (target: Int)(using MyContext, Long)(using mt: MyCaseClass[_], cs: CharSequence)
         |    override def myExt7(p1: Int)(using u1: Short, u2: MyTrait)(p2: Long): String = ???
         |""".stripMargin
    addHelperClassesForExtensionTests()
    runImplementAllTest(before, after)
  }

  def testExtension_WithUsingClausesInTheBeginningOfParametersClause(): Unit = {
    val before =
      s"""abstract class MyBaseClass:
         |  extension (using MyContext)(using Long)(target: Int)(using mt: MyCaseClass[_], cs: CharSequence)
         |    def myExt8(using u1: Short, u2: MyTrait)(p1: Int)(p2: Long): String
         |
         |class ${CARET}MyChildClass extends MyBaseClass
         |""".stripMargin
    val after =
      s"""abstract class MyBaseClass:
         |  extension (using MyContext)(using Long)(target: Int)(using mt: MyCaseClass[_], cs: CharSequence)
         |    def myExt8(using u1: Short, u2: MyTrait)(p1: Int)(p2: Long): String
         |
         |class MyChildClass extends MyBaseClass:
         |  extension (using MyContext)(using Long)(target: Int)(using mt: MyCaseClass[_], cs: CharSequence)
         |    override def myExt8(using u1: Short, u2: MyTrait)(p1: Int)(p2: Long): String = ???
         |""".stripMargin
    addHelperClassesForExtensionTests()
    runImplementAllTest(before, after)
  }

  def testExtension_CopyTargetNameAnnotation(): Unit = {
    val before =
      s"""import scala.annotation.targetName
         |
         |abstract class MyBaseClass:
         |  extension (target: String)
         |    @targetName("myExtTargetNameTest1_Renamed")
         |    def myExtTargetNameTest1: String
         |    @targetName("myExtTargetNameTest2_Renamed")
         |    def myExtTargetNameTest2: String
         |
         |class ${CARET}MyChildClass extends MyBaseClass
         |""".stripMargin
    val after =
      s"""import scala.annotation.targetName
         |
         |abstract class MyBaseClass:
         |  extension (target: String)
         |    @targetName("myExtTargetNameTest1_Renamed")
         |    def myExtTargetNameTest1: String
         |    @targetName("myExtTargetNameTest2_Renamed")
         |    def myExtTargetNameTest2: String
         |
         |class MyChildClass extends MyBaseClass:
         |  extension (target: String)
         |    @targetName("myExtTargetNameTest1_Renamed")
         |    override def myExtTargetNameTest1: String = ???
         |    @targetName("myExtTargetNameTest2_Renamed")
         |    override def myExtTargetNameTest2: String = ???
         |""".stripMargin

    runImplementAllTest(before, after)
  }

  def testExtension_ComplicatedMixedExample(): Unit = {
    val before =
      s"""abstract class MyBaseClass[TypeParamInBaseClass]:
         |  extension [T <: TypeParamInBaseClass, T2 <: MyTrait](using TypeParamInBaseClass, Long)(target: TypeParamInBaseClass)(using mt: MyCaseClass[_], cs: CharSequence)
         |    def myExtComplex[E <: TypeParamInBaseClass, E2 <: MyTrait](a: TypeParamInBaseClass)(using b: TypeParamInBaseClass, e: E)(t: T): String
         |
         |class ${CARET}MyChildClass extends MyBaseClass[MyClass]
         |""".stripMargin
    val after =
      s"""abstract class MyBaseClass[TypeParamInBaseClass]:
         |  extension [T <: TypeParamInBaseClass, T2 <: MyTrait](using TypeParamInBaseClass, Long)(target: TypeParamInBaseClass)(using mt: MyCaseClass[_], cs: CharSequence)
         |    def myExtComplex[E <: TypeParamInBaseClass, E2 <: MyTrait](a: TypeParamInBaseClass)(using b: TypeParamInBaseClass, e: E)(t: T): String
         |
         |class MyChildClass extends MyBaseClass[MyClass]:
         |  extension [T <: MyClass, T2 <: MyTrait](using MyClass, Long)(target: MyClass)(using mt: MyCaseClass[_], cs: CharSequence)
         |    override def myExtComplex[E <: MyClass, E2 <: MyTrait](a: MyClass)(using b: MyClass, e: E)(t: T): String = ???
         |""".stripMargin
    addHelperClassesForExtensionTests()
    runImplementAllTest(before, after)
  }

  def testExtension_ComplicatedMixedExample_Override(): Unit = {
    val before =
      s"""abstract class MyBaseClass[TypeParamInBaseClass]:
         |  extension [T <: TypeParamInBaseClass, T2 <: MyTrait](using TypeParamInBaseClass, Long)(target: TypeParamInBaseClass)(using mt: MyCaseClass[_], cs: CharSequence)
         |    def myExtComplex[E <: TypeParamInBaseClass, E2 <: MyTrait](a: TypeParamInBaseClass)(using b: TypeParamInBaseClass, e: E)(t: T): String = ???
         |
         |class ${CARET}MyChildClass extends MyBaseClass[MyClass]
         |""".stripMargin
    val after =
      s"""abstract class MyBaseClass[TypeParamInBaseClass]:
         |  extension [T <: TypeParamInBaseClass, T2 <: MyTrait](using TypeParamInBaseClass, Long)(target: TypeParamInBaseClass)(using mt: MyCaseClass[_], cs: CharSequence)
         |    def myExtComplex[E <: TypeParamInBaseClass, E2 <: MyTrait](a: TypeParamInBaseClass)(using b: TypeParamInBaseClass, e: E)(t: T): String = ???
         |
         |class MyChildClass extends MyBaseClass[MyClass]:
         |  extension [T <: MyClass, T2 <: MyTrait](using MyClass, Long)(target: MyClass)(using mt: MyCaseClass[_], cs: CharSequence)
         |    override def myExtComplex[E <: MyClass, E2 <: MyTrait](a: MyClass)(using b: MyClass, e: E)(t: T): String = super.myExtComplex(target)(a)(t)
         |""".stripMargin
    addHelperClassesForExtensionTests()
    runTest("myExtComplex", before, after, isImplement = false)
  }

  def testExtensionImplementedInNestedIndentationBasedSyntax(): Unit = {
    runImplementAllTest(
      s"""abstract class MyBaseFromScala3:
         |  extension (target: String)
         |    def myExt1(p: String): String
         |    def myExt2(p: String): String
         |  extension (target: String)
         |    def myExt3(p: String): String
         |    def myExt4(p: String): String
         |
         |object wrapper1:
         |  object wrapper2:
         |    object wrapper3:
         |      class ${CARET}MyChildInScala3 extends MyBaseFromScala3
         |""".stripMargin,
      """abstract class MyBaseFromScala3:
        |  extension (target: String)
        |    def myExt1(p: String): String
        |    def myExt2(p: String): String
        |  extension (target: String)
        |    def myExt3(p: String): String
        |    def myExt4(p: String): String
        |
        |object wrapper1:
        |  object wrapper2:
        |    object wrapper3:
        |      class MyChildInScala3 extends MyBaseFromScala3:
        |        extension (target: String)
        |          override def myExt1(p: String): String = ???
        |          override def myExt2(p: String): String = ???
        |        extension (target: String)
        |          override def myExt3(p: String): String = ???
        |          override def myExt4(p: String): String = ???
        |""".stripMargin
    )
  }

  def testExtensionMethodMemberPresentableText(): Unit = {
    val fileText =
      """class MyClass:
        |  extension (target: String)
        |    def myExt1: String = ???
        |""".stripMargin

    assertMembersPresentableText[ScExtensionMethodMember](
      fileText,
      "MyClass",
      _ => true,
      """(target: String) myExt1: String""",
    )
  }

  def testExtensionMethodMemberPresentableText_TypeParametersInExtensionMember(): Unit = {
    val fileText =
      """class MyClass:
        |  extension [T <: CharSequence, E](target: T)(using c: E)
        |    def myExt1[X <: CharSequence, Y]: String = ???
        |""".stripMargin

    assertMembersPresentableText[ScExtensionMethodMember](
      fileText,
      "MyClass",
      _ => true,
      """[T <: CharSequence, E](target: T)(c: E) myExt1[X <: CharSequence, Y]: String""",
    )
  }

  def testExtensionMethodMemberPresentableText_UsingParametersInExtensionMember(): Unit = {
    val fileText =
      """class MyClass:
        |  extension (using context1: Int)(target: String)(using context2: Long)
        |    def myExt2(using context3: Float)(param1: String, param2: String)(using context4: Double): String = ???
        |""".stripMargin

    assertMembersPresentableText[ScExtensionMethodMember](
      fileText,
      "MyClass",
      _ => true,
      """(using context1: Int)(target: String)(using context2: Long) myExt2(context3: Float)(param1: String, param2: String)(context4: Double): String""",
    )
  }
}
