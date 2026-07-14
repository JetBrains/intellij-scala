package org.jetbrains.plugins.scala.structureView

import org.jetbrains.plugins.scala.ScalaVersion

class ScalaNavBarModelExtensionTest extends ScalaNavBarModelExtensionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  def testPathAtCaret_TopLevel_Class(): Unit = assertNavBarPathAtCaret(
    s"""class Top${CARET}Class(value: Int)""".stripMargin,
    Seq("aaa.scala", "TopClass")
  )

  def testPathAtCaret_TopLevel_Trait(): Unit = assertNavBarPathAtCaret(
    s"""trait Top${CARET}Trait""".stripMargin,
    Seq("aaa.scala", "TopTrait")
  )

  def testPathAtCaret_TopLevel_Object(): Unit = assertNavBarPathAtCaret(
    s"""object Top${CARET}Object""".stripMargin,
    Seq("aaa.scala", "TopObject")
  )

  def testPathAtCaret_TopLevel_Enum(): Unit = assertNavBarPathAtCaret(
    s"""enum Top${CARET}Enum:
       |  case TopCase
       |""".stripMargin,
    Seq("aaa.scala", "TopEnum")
  )

  def testPathAtCaret_TopLevel_Enum_Case(): Unit = assertNavBarPathAtCaret(
    s"""enum TopEnum:
       |  case Top${CARET}Case(value: Int)
       |""".stripMargin,
    Seq("aaa.scala", "TopEnum", "TopCase")
  )

  def testPathAtCaret_TopLevel_Enum_CaseExtendsArgument(): Unit = assertNavBarPathAtCaret(
    s"""enum Planet(mass: Double):
       |  case Earth extends Planet(identity(${CARET}1.0))
       |""".stripMargin,
    Seq("aaa.scala", "Planet", "Earth")
  )

  def testPathAtCaret_TopLevel_Enum_MemberAfterCase(): Unit = assertNavBarPathAtCaret(
    s"""enum TopEnum:
       |  case TopCase
       |  def enumMember: Int = identity(${CARET}1)
       |""".stripMargin,
    Seq("aaa.scala", "TopEnum", "enumMember")
  )

  def testPathAtCaret_TopLevel_TypeAlias(): Unit = assertNavBarPathAtCaret(
    s"""type Top${CARET}Alias = String""".stripMargin,
    Seq("aaa.scala", "TopAlias")
  )

  def testPathAtCaret_TopLevel_Val(): Unit = assertNavBarPathAtCaret(
    s"""val topVal = identity(${CARET}1)""".stripMargin,
    Seq("aaa.scala", "topVal")
  )

  def testPathAtCaret_TopLevel_Var(): Unit = assertNavBarPathAtCaret(
    s"""var topVar = identity(${CARET}2)""".stripMargin,
    Seq("aaa.scala", "topVar")
  )

  def testPathAtCaret_TopLevel_Def(): Unit = assertNavBarPathAtCaret(
    s"""def topDef(arg: String): String = identity(${CARET}arg)""".stripMargin,
    Seq("aaa.scala", "topDef")
  )

  def testPathAtCaret_TopLevel_Extension_Method(): Unit = assertNavBarPathAtCaret(
    s"""extension (value: String)
       |  def topExtension: String = identity(${CARET}value)
       |""".stripMargin,
    Seq("aaa.scala", "extension", "topExtension")
  )

  def testPathAtCaret_TopLevel_GivenAlias_Named(): Unit = assertNavBarPathAtCaret(
    s"""given top${CARET}Given: String = "value" """.stripMargin,
    Seq("aaa.scala", "topGiven")
  )

  def testPathAtCaret_TopLevel_GivenAlias_Anonymous(): Unit = assertNavBarPathAtCaret(
    s"""given Str${CARET}ing = "value" """.stripMargin,
    Seq("aaa.scala", "given_String")
  )

  def testPathAtCaret_TopLevel_GivenInstance_Named(): Unit = assertNavBarPathAtCaret(
    s"""given top${CARET}Given: AutoCloseable with
       |  override def close(): Unit = ()
       |""".stripMargin,
    Seq("aaa.scala", "topGiven")
  )

  def testPathAtCaret_TopLevel_GivenInstance_Anonymous(): Unit = assertNavBarPathAtCaret(
    s"""given Auto${CARET}Closeable with
       |  override def close(): Unit = ()
       |""".stripMargin,
    Seq("aaa.scala", "given_AutoCloseable")
  )

  def testPathAtCaret_TopLevel_Val_SbtStyleInitializer(): Unit = assertNavBarPathAtCaret(
    s"""val root = project.in(file("."))
       |  .settings(
       |    libraryDependencies ++= Seq(
       |      $CARET
       |    )
       |  )
       |""".stripMargin,
    Seq("aaa.scala", "root")
  )

  def testPathAtCaret_TopLevel_Val_BuildSbtFile(): Unit = assertNavBarPathAtCaretInFileWithLanguageId(
    "build.sbt",
    Set("sbt", "sbt Scala 3"),
    s"""val root = project.in(file("."))
       |  .settings(
       |    libraryDependencies ++= Seq(
       |      $CARET
       |    )
       |  )
       |""".stripMargin,
    Seq("build.sbt", "root")
  )

  def testPathAtCaret_TopLevel_Val_WorksheetFile(): Unit = assertNavBarPathAtCaretInFileWithLanguageId(
    "worksheet.sc",
    Set("Scala Worksheet", "Scala 3 Worksheet"),
    s"""val worksheetValue = identity(${CARET}1)
       |""".stripMargin,
    Seq("worksheet.sc", "worksheetValue")
  )

  def testPathAtCaret_ScratchFile_CompanionClass_MemberDef(): Unit = assertNavBarPathAtCaretInFile(
    "scratch.sc",
    s"""class MyClass {
       |  def foo(): Unit = {
       |    $CARET
       |  }
       |}
       |
       |object MyClass {
       |  def foo(): Unit = {
       |  }
       |}
       |""".stripMargin,
    Seq("scratch.sc", "MyClass", "foo")
  )

  def testPathAtCaret_FileRepresentative_SingleClass(): Unit = assertNavBarPathAtCaretInFile(
    "SingleClass.scala",
    s"class SingleClass(value: Int) { $CARET } ",
    Seq("SingleClass")
  )

  def testPathAtCaret_FileRepresentative_MemberDef_InSingleClass(): Unit = assertNavBarPathAtCaretInFile(
    "Example.scala",
    s"""class Example {
       |  def foo(): Unit = {
       |    $CARET
       |  }
       |
       |  def bar(): Unit = {}
       |}""".stripMargin,
    Seq("Example", "foo")
  )

  def testPathAtCaret_FileRepresentative_SingleCaseClass(): Unit = assertNavBarPathAtCaretInFile(
    "SingleCaseClass.scala",
    s"""case class Single${CARET}CaseClass(value: Int)""".stripMargin,
    Seq("SingleCaseClass")
  )

  def testPathAtCaret_FileRepresentative_SingleTrait(): Unit = assertNavBarPathAtCaretInFile(
    "SingleTrait.scala",
    s"""trait Single${CARET}Trait""".stripMargin,
    Seq("SingleTrait")
  )

  def testPathAtCaret_FileRepresentative_SingleObject(): Unit = assertNavBarPathAtCaretInFile(
    "SingleObject.scala",
    s"""object Single${CARET}Object""".stripMargin,
    Seq("SingleObject")
  )

  def testPathAtCaret_FileRepresentative_SingleCaseObject(): Unit = assertNavBarPathAtCaretInFile(
    "SingleCaseObject.scala",
    s"""case object Single${CARET}CaseObject""".stripMargin,
    Seq("SingleCaseObject")
  )

  def testPathAtCaret_FileRepresentative_SingleEnum(): Unit = assertNavBarPathAtCaretInFile(
    "SingleEnum.scala",
    s"""enum Single${CARET}Enum:
       |  case A
       |""".stripMargin,
    Seq("SingleEnum")
  )

  def testPathAtCaret_FileRepresentative_PackageObject(): Unit = assertNavBarPathAtCaretInFile(
    "package.scala",
    s"""package object fo${CARET}o""".stripMargin,
    Seq("foo")
  )

  def testPathAtCaret_FileRepresentative_Companions_ClassAndObject(): Unit = assertNavBarPathAtCaretInFile(
    "Companion.scala",
    s"""class Com${CARET}panion
       |object Companion
       |""".stripMargin,
    Seq("Companion")
  )

  def testPathAtCaret_FileRepresentative_Companions_ObjectAndClass(): Unit = assertNavBarPathAtCaretInFile(
    "Companion.scala",
    s"""object Com${CARET}panion
       |class Companion
       |""".stripMargin,
    Seq("Companion")
  )

  def testPathAtCaret_FileRepresentative_Companions_TraitAndObject(): Unit = assertNavBarPathAtCaretInFile(
    "Companion.scala",
    s"""trait Com${CARET}panion
       |object Companion
       |""".stripMargin,
    Seq("Companion")
  )

  def testPathAtCaret_FileRepresentative_Companions_EnumAndObject(): Unit = assertNavBarPathAtCaretInFile(
    "Companion.scala",
    s"""enum Com${CARET}panion:
       |  case A
       |object Companion
       |""".stripMargin,
    Seq("Companion")
  )

  def testPathAtCaret_FileRepresentative_Companions_OpaqueTypeAndObject(): Unit = assertNavBarPathAtCaretInFile(
    "Companion.scala",
    s"""opaque type Com${CARET}panion = Int
       |object Companion
       |""".stripMargin,
    Seq("Companion")
  )

  def testAdjustElement_FileRepresentative_SingleClass(): Unit = assertAdjustedNavBarPath(
    "SingleClass.scala",
    "class SingleClass",
    Seq("SingleClass")
  )

  def testAdjustElement_FileRepresentative_Companions_ClassAndObject(): Unit = assertAdjustedNavBarPath(
    "Companion.scala",
    s"""class Companion
       |object Companion
       |""".stripMargin,
    Seq("Companion")
  )

  def testPathAtCaret_FileRepresentative_NotApplied_DifferentFileName(): Unit = assertNavBarPathAtCaretInFile(
    "Other.scala",
    s"""class Single${CARET}Class""".stripMargin,
    Seq("Other.scala", "SingleClass")
  )

  def testPathAtCaret_FileRepresentative_NotApplied_AdditionalTopLevelClass(): Unit = assertNavBarPathAtCaretInFile(
    "SingleClass.scala",
    s"""class Single${CARET}Class
       |class Other
       |""".stripMargin,
    Seq("SingleClass.scala", "SingleClass")
  )

  def testPathAtCaret_FileRepresentative_NotApplied_TopLevelDef(): Unit = assertNavBarPathAtCaretInFile(
    "SingleClass.scala",
    s"""class Single${CARET}Class
       |def helper = 1
       |""".stripMargin,
    Seq("SingleClass.scala", "SingleClass")
  )

  def testPathAtCaret_FileRepresentative_NotApplied_CompanionsWithTopLevelDef(): Unit = assertNavBarPathAtCaretInFile(
    "Companion.scala",
    s"""class Com${CARET}panion
       |object Companion
       |def helper = 1
       |""".stripMargin,
    Seq("Companion.scala", "Companion")
  )

  def testPathAtCaret_FileRepresentative_NotApplied_DifferentCompanionNames(): Unit = assertNavBarPathAtCaretInFile(
    "Companion.scala",
    s"""class Com${CARET}panion
       |object Other
       |""".stripMargin,
    Seq("Companion.scala", "Companion")
  )

  def testPathAtCaret_Member_ClassParameter(): Unit = assertNavBarPathAtCaret(
    s"""class Wrapper(val class${CARET}Param: Int)""".stripMargin,
    Seq("aaa.scala", "Wrapper", "classParam")
  )

  def testPathAtCaret_Member_Val_Initializer_BlockSyntax(): Unit = assertNavBarPathAtCaret(
    s"""class C {
       |  val name1 = 1$CARET
       |  def name2 = 2
       |}""".stripMargin,
    Seq("aaa.scala", "C", "name1")
  )

  def testPathAtCaret_Member_Val_Initializer_IndentationSyntax(): Unit = assertNavBarPathAtCaret(
    s"""class Wrapper:
       |  val memberVal = identity(${CARET}1)
       |""".stripMargin,
    Seq("aaa.scala", "Wrapper", "memberVal")
  )

  def testPathAtCaret_Member_Var_Initializer_IndentationSyntax(): Unit = assertNavBarPathAtCaret(
    s"""class Wrapper:
       |  var memberVar = identity(${CARET}2)
       |""".stripMargin,
    Seq("aaa.scala", "Wrapper", "memberVar")
  )

  def testPathAtCaret_Member_Val_TuplePattern_BlockSyntax(): Unit = assertNavBarPathAtCaret(
    s"""class C {
       |  val (foo, bar) = (1, 2$CARET)
       |}""".stripMargin,
    Seq("aaa.scala", "C", "(foo, bar)")
  )

  def testPathAtCaret_Member_Val_TuplePattern_IndentationSyntax(): Unit = assertNavBarPathAtCaret(
    s"""class Wrapper:
       |  val (foo, bar) = (1, 2$CARET)
       |""".stripMargin,
    Seq("aaa.scala", "Wrapper", "(foo, bar)")
  )

  def testPathAtCaret_Member_Var_TuplePattern_BlockSyntax(): Unit = assertNavBarPathAtCaret(
    s"""class C {
       |  var (foo, bar) = (1, 2$CARET)
       |}""".stripMargin,
    Seq("aaa.scala", "C", "(foo, bar)")
  )

  def testPathAtCaret_Member_Var_TuplePattern_IndentationSyntax(): Unit = assertNavBarPathAtCaret(
    s"""class Wrapper:
       |  var (foo, bar) = (1, 2$CARET)
       |""".stripMargin,
    Seq("aaa.scala", "Wrapper", "(foo, bar)")
  )

  def testPathAtCaret_Member_Val_ExtractorPattern_BlockSyntax(): Unit = assertNavBarPathAtCaret(
    s"""class C {
       |  val Some(_) = optionalCall()$CARET
       |}""".stripMargin,
    Seq("aaa.scala", "C", "Some(_)")
  )

  def testPathAtCaret_Member_Val_ExtractorPattern_IndentationSyntax(): Unit = assertNavBarPathAtCaret(
    s"""class Wrapper:
       |  val Some(_) = Option(${CARET}1)
       |""".stripMargin,
    Seq("aaa.scala", "Wrapper", "Some(_)")
  )

  def testPathAtCaret_Member_Def(): Unit = assertNavBarPathAtCaret(
    s"""class Wrapper:
       |  def memberDef(arg: String): String = identity(${CARET}arg)
       |""".stripMargin,
    Seq("aaa.scala", "Wrapper", "memberDef")
  )

  def testPathAtCaret_Member_Def_Parameter(): Unit = assertNavBarPathAtCaret(
    s"""class Wrapper:
       |  def memberDef(par${CARET}am: String): String = param
       |""".stripMargin,
    Seq("aaa.scala", "Wrapper", "memberDef")
  )

  def testPathAtCaret_Member_SecondaryConstructor(): Unit = assertNavBarPathAtCaret(
    s"""class Wrapper:
       |  def this(arg: Int) =
       |    this()
       |    identity(${CARET}arg)
       |""".stripMargin,
    Seq("aaa.scala", "Wrapper", "this")
  )

  def testPathAtCaret_Member_TypeAlias(): Unit = assertNavBarPathAtCaret(
    s"""class Wrapper:
       |  type Member${CARET}Alias = String
       |""".stripMargin,
    Seq("aaa.scala", "Wrapper", "MemberAlias")
  )

  def testPathAtCaret_Member_InnerClass(): Unit = assertNavBarPathAtCaret(
    s"""class Wrapper:
       |  class Inner${CARET}Class
       |""".stripMargin,
    Seq("aaa.scala", "Wrapper", "InnerClass")
  )

  def testPathAtCaret_Member_InnerTrait(): Unit = assertNavBarPathAtCaret(
    s"""class Wrapper:
       |  trait Inner${CARET}Trait
       |""".stripMargin,
    Seq("aaa.scala", "Wrapper", "InnerTrait")
  )

  def testPathAtCaret_Member_InnerObject(): Unit = assertNavBarPathAtCaret(
    s"""class Wrapper:
       |  object Inner${CARET}Object
       |""".stripMargin,
    Seq("aaa.scala", "Wrapper", "InnerObject")
  )

  def testPathAtCaret_Member_InnerEnum(): Unit = assertNavBarPathAtCaret(
    s"""class Wrapper:
       |  enum Inner${CARET}Enum:
       |    case InnerCase
       |""".stripMargin,
    Seq("aaa.scala", "Wrapper", "InnerEnum")
  )

  def testPathAtCaret_Member_InnerEnum_Case(): Unit = assertNavBarPathAtCaret(
    s"""class Wrapper:
       |  enum InnerEnum:
       |    case Inner${CARET}Case
       |""".stripMargin,
    Seq("aaa.scala", "Wrapper", "InnerEnum", "InnerCase")
  )

  def testPathAtCaret_Member_Extension_Method(): Unit = assertNavBarPathAtCaret(
    s"""class Wrapper:
       |  extension (value: String)
       |    def memberExtension: String = identity(${CARET}value)
       |""".stripMargin,
    Seq("aaa.scala", "Wrapper", "extension", "memberExtension")
  )

  def testPathAtCaret_Member_GivenAlias_Named(): Unit = assertNavBarPathAtCaret(
    s"""class Wrapper:
       |  given member${CARET}Given: String = "value"
       |""".stripMargin,
    Seq("aaa.scala", "Wrapper", "memberGiven")
  )

  def testPathAtCaret_Member_GivenAlias_Anonymous(): Unit = assertNavBarPathAtCaret(
    s"""class Wrapper:
       |  given Str${CARET}ing = "value"
       |""".stripMargin,
    Seq("aaa.scala", "Wrapper", "given_String")
  )

  def testPathAtCaret_Member_GivenInstance_Named(): Unit = assertNavBarPathAtCaret(
    s"""class Wrapper:
       |  given member${CARET}Given: AutoCloseable with
       |    override def close(): Unit = ()
       |""".stripMargin,
    Seq("aaa.scala", "Wrapper", "memberGiven")
  )

  def testPathAtCaret_Member_GivenInstance_Anonymous(): Unit = assertNavBarPathAtCaret(
    s"""class Wrapper:
       |  given Auto${CARET}Closeable with
       |    override def close(): Unit = ()
       |""".stripMargin,
    Seq("aaa.scala", "Wrapper", "given_AutoCloseable")
  )

  def testPathAtCaret_Local_Def(): Unit = assertNavBarPathAtCaret(
    s"""class Wrapper:
       |  def outer(): Int =
       |    def localDef(): Int = identity(${CARET}1)
       |    localDef()
       |""".stripMargin,
    Seq("aaa.scala", "Wrapper", "outer", "localDef")
  )

  // Nested def definitions
  def testPathAtCaret_NestedDefs_InsideMyDef4(): Unit = assertNavBarPathAtCaret(
    s"class MyClass { def myDef1 = { def myDef2 = { class MyClassInner { def myDef3 = { def myDef4 = { $CARET } } } } } }",
    Seq("aaa.scala", "MyClass", "myDef1", "myDef2", "MyClassInner", "myDef3", "myDef4")
  )

  def testPathAtCaret_NestedDefs_InsideMyDef3(): Unit = assertNavBarPathAtCaret(
    s"class MyClass { def myDef1 = { def myDef2 = { class MyClassInner { def myDef3 = { $CARET def myDef4 = { } } } } } }",
    Seq("aaa.scala", "MyClass", "myDef1", "myDef2", "MyClassInner", "myDef3")
  )

  def testPathAtCaret_NestedDefs_InsideMyDef2(): Unit = assertNavBarPathAtCaret(
    s"class MyClass { def myDef1 = { def myDef2 = { $CARET class MyClassInner { def myDef3 = { def myDef4 = { } } } } } }",
    Seq("aaa.scala", "MyClass", "myDef1", "myDef2")
  )

  def testPathAtCaret_NestedDefs_InsideMyDef1(): Unit = assertNavBarPathAtCaret(
    s"class MyClass { def myDef1 = { $CARET def myDef2 = { class MyClassInner { def myDef3 = { def myDef4 = { } } } } } }",
    Seq("aaa.scala", "MyClass", "myDef1")
  )

  // Nested val definitions
  def testPathAtCaret_NestedVals_InsideMyVal4(): Unit = assertNavBarPathAtCaret(
    s"class MyClass { val myVal1 = { val myVal2 = { class MyClassInner { val myVal3 = { val myVal4 = { $CARET } } } } } }",
    Seq("aaa.scala", "MyClass", "myVal1", "myVal2", "MyClassInner", "myVal3", "myVal4")
  )

  def testPathAtCaret_NestedVals_InsideMyVal3(): Unit = assertNavBarPathAtCaret(
    s"class MyClass { val myVal1 = { val myVal2 = { class MyClassInner { val myVal3 = { $CARET val myVal4 = { } } } } } }",
    Seq("aaa.scala", "MyClass", "myVal1", "myVal2", "MyClassInner", "myVal3")
  )

  def testPathAtCaret_NestedVals_InsideMyVal2(): Unit = assertNavBarPathAtCaret(
    s"class MyClass { val myVal1 = { val myVal2 = { $CARET class MyClassInner { val myVal3 = { val myVal4 = { } } } } } }",
    Seq("aaa.scala", "MyClass", "myVal1", "myVal2")
  )

  def testPathAtCaret_NestedVals_InsideMyVal1(): Unit = assertNavBarPathAtCaret(
    s"class MyClass { val myVal1 = { $CARET val myVal2 = { class MyClassInner { val myVal3 = { val myVal4 = { } } } } } }",
    Seq("aaa.scala", "MyClass", "myVal1")
  )

  def testPathAtCaret_Local_Class(): Unit = assertNavBarPathAtCaret(
    s"""class Wrapper:
       |  def outer(): Unit =
       |    class Local${CARET}Class
       |""".stripMargin,
    Seq("aaa.scala", "Wrapper", "outer", "LocalClass")
  )

  def testPathAtCaret_Local_Object_MemberDef(): Unit = assertNavBarPathAtCaret(
    s"""class Wrapper:
       |  def outer(): Unit =
       |    object LocalObject:
       |      def inner: Int = identity(${CARET}1)
       |""".stripMargin,
    Seq("aaa.scala", "Wrapper", "outer", "LocalObject", "inner")
  )

  def testPathAtCaret_Local_Enum_Case(): Unit = assertNavBarPathAtCaret(
    s"""class Wrapper:
       |  def outer(): Unit =
       |    enum LocalEnum:
       |      case Local${CARET}Case
       |""".stripMargin,
    Seq("aaa.scala", "Wrapper", "outer", "LocalEnum", "LocalCase")
  )

  def testPathAtCaret_Local_Val_ReturnsEnclosingDef(): Unit = assertNavBarPathAtCaret(
    s"""class Wrapper:
       |  def outer(): Int =
       |    val localVal = identity(${CARET}1)
       |    localVal
       |""".stripMargin,
    Seq("aaa.scala", "Wrapper", "outer")
  )

  def testPathAtCaret_Local_LambdaBody_ReturnsEnclosingDef(): Unit = assertNavBarPathAtCaret(
    s"""class Wrapper:
       |  def outer(): Int =
       |    List(1).map { value =>
       |      identity(${CARET}value)
       |    }.sum
       |""".stripMargin,
    Seq("aaa.scala", "Wrapper", "outer")
  )

  def testPresentableText_AnonymousClass(): Unit = assertNavBarPathAtCaret(
    s"""class Wrapper {
       |  val value = new Object() {
       |     val innerValue = $CARET???
       |  }
       |}
       |""".stripMargin,
    Seq("aaa.scala", "Wrapper", "value", "anonymous Object", "innerValue")
  )
}
