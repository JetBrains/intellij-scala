package org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3.Scala3TestDataBracelessCode.CodeWithDebugName.reservedDebugNames

import scala.collection.mutable

/** for `try/catch` and `match` test data see [[Scala3TestDataCaseClausesEditorStates]] */
private[editor]
object Scala3TestDataBracelessCode {

  private val Caret = EditorTestUtil.CARET_TAG
  private val InjectedCodePlaceholder = "__InjectedCodePlaceholder__"

  def injectCodeWithIndentAdjust(injectedCode: String, contextCode: String): String =
    TestIndentUtils.injectCodeWithIndentAdjust(injectedCode, contextCode, InjectedCodePlaceholder)

  case class CodeWithDebugName(code: String, debugName: String) {
    reservedDebugNames.synchronized {
      if (reservedDebugNames.contains(debugName))
        throw new AssertionError(s"code debug name is not unique: '$debugName'")
      else
        reservedDebugNames += debugName
    }
    def withTransformedDebugName(f: String => String): CodeWithDebugName =
      CodeWithDebugName(code, f(debugName))
  }
  object CodeWithDebugName {
    private val reservedDebugNames = mutable.HashSet.empty[String]
  }

  //noinspection TypeAnnotation
  // TODO: preformat test data before running tests in order to ensure that we use the correct indent
  object WrapperCodeContexts {

    val Empty = CodeWithDebugName(
      s"$InjectedCodePlaceholder",
      "EmptyFile"
    )

    val TopLevel =
      CodeWithDebugName(
        s"""$InjectedCodePlaceholder
           |
           |class OuterClass
           |""".stripMargin,
        "TopLevel"
      )
    val TopLevel_LastStatement =
      CodeWithDebugName(
        s"""class OuterClass
           |
           |$InjectedCodePlaceholder
           |""".stripMargin,
        "TopLevel LastStatement"
      )
    val TopLevel_LastStatement_NoNewLineAtEOF =
      CodeWithDebugName(
        s"""class OuterClass
           |
           |$InjectedCodePlaceholder""".stripMargin,
        "TopLevel LastStatement NoNewLineAtEOF"
      )

    val ClassWithBraces =
      CodeWithDebugName(
        s"""class MyClass {
           |  $InjectedCodePlaceholder
           |
           |  println("42")
           |}""".stripMargin,
        "ClassWithBraces"
      )
    val ClassWithBraces_LastStatement =
      CodeWithDebugName(
        s"""class MyClass {
           |  println("42")
           |
           |  $InjectedCodePlaceholder
           |}""".stripMargin,
        "ClassWithBraces_LastStatement"
      )
    val ClassWithColonAndEndMarker =
      CodeWithDebugName(
        s"""class MyClass:
           |  $InjectedCodePlaceholder
           |
           |  println("42")
           |end MyClass""".stripMargin,
        "ClassWithColonAndEndMarker"
      )
    val ClassWithColonAndEndMarker_LastStatement =
      CodeWithDebugName(
        s"""class MyClass:
           |  println("42")
           |
           |  $InjectedCodePlaceholder
           |end MyClass""".stripMargin,
        "ClassWithColonAndEndMarker_LastStatement"
      )

    val ClassWithColonWithoutEndMarker_LastStatement_1 =
      CodeWithDebugName(
        s"""class MyClass:
           |  println("42")
           |
           |  $InjectedCodePlaceholder
           |""".stripMargin,
        "ClassWithColonWithoutEndMarker_LastStatement_1"
      )

    val ClassWithColonWithoutEndMarker_LastStatement_2 =
      CodeWithDebugName(
        s"""class MyClass:
           |  println("42")
           |
           |  $InjectedCodePlaceholder
           |
           |class AnotherClass""".stripMargin,
        "ClassWithColonWithoutEndMarker_LastStatement_2"
      )

    val NestedClassWithBraces =
      CodeWithDebugName(
        s"""class OuterClass1 {
           |  class OuterClass2 {
           |    $InjectedCodePlaceholder
           |
           |    class Inner
           |  }
           |}""".stripMargin,
        "NestedClassWithBraces"
      )
    val NestedClassWithBraces_LastStatement =
      CodeWithDebugName(
        s"""class OuterClass1 {
           |  class OuterClass2 {
           |    class Inner
           |
           |    $InjectedCodePlaceholder
           |  }
           |}""".stripMargin,
        "NestedClassWithBraces_LastStatement"
      )
    val NestedClassWithColonAndEndMarker =
      CodeWithDebugName(
        s"""class OuterClass1:
           |  class OuterClass2:
           |    $InjectedCodePlaceholder
           |
           |    class Inner
           |  end OuterClass2
           |end OuterClass1""".stripMargin,
        "NestedClassWithColonAndEndMarker"
      )
    val NestedClassWithColonAndEndMarker_LastStatement =
      CodeWithDebugName(
        s"""class OuterClass1:
           |  class OuterClass2:
           |    class Inner
           |
           |    $InjectedCodePlaceholder
           |  end OuterClass2
           |end OuterClass1""".stripMargin,
        "NestedClassWithColonAndEndMarker_LastStatement"
      )
    val NestedClassWithColonWithoutEndMarker =
      CodeWithDebugName(
        s"""class OuterClass1:
           |  class OuterClass2:
           |    $InjectedCodePlaceholder
           |
           |    class Inner
           |""".stripMargin,
        "NestedClassWithColonWithoutEndMarker"
      )
    val NestedClassWithColonWithoutEndMarker_LastStatement =
      CodeWithDebugName(
        s"""class OuterClass1:
           |  class OuterClass2:
           |    class Inner
           |
           |    $InjectedCodePlaceholder
           |""".stripMargin,
        "NestedClassWithColonWithoutEndMarker_LastStatement"
      )

    val DeeplyNestedInVariousScopes1 = CodeWithDebugName(
      s"""class OuterClass {
         |  def foo =
         |    val res =
         |      $InjectedCodePlaceholder
         |}
         |""".stripMargin,
      "DeeplyNestedInVariousScopes1"
    )
    val DeeplyNestedInVariousScopes2 = CodeWithDebugName(
      s"""class OuterClass:
         |  def foo =
         |    val res =
         |      $InjectedCodePlaceholder
         |end A
         |""".stripMargin,
      "DeeplyNested3"
    )

    val InsideCaseClausesLast = CodeWithDebugName(
      s"""1 match
         |  case 1 =>
         |  case 2 =>
         |    $InjectedCodePlaceholder
         |""".stripMargin,
      "InsideCaseClausesLast"
    )
    val InsideCaseClausesNonLast = CodeWithDebugName(
      s"""1 match
         |  case 1 =>
         |    $InjectedCodePlaceholder
         |  case 2 =>
         |""".stripMargin,
      "InsideCaseClausesNonLast"
    )

    val AllContexts: Seq[CodeWithDebugName] = Seq(
      TopLevel,
      TopLevel_LastStatement,
      TopLevel_LastStatement_NoNewLineAtEOF,
      //
      ClassWithBraces,
      ClassWithBraces_LastStatement,
      ClassWithColonAndEndMarker,
      ClassWithColonAndEndMarker_LastStatement,
      ClassWithColonWithoutEndMarker_LastStatement_1,
      ClassWithColonWithoutEndMarker_LastStatement_2,
      //
      NestedClassWithBraces,
      NestedClassWithBraces_LastStatement,
      NestedClassWithColonAndEndMarker,
      NestedClassWithColonAndEndMarker_LastStatement,
      NestedClassWithColonWithoutEndMarker,
      NestedClassWithColonWithoutEndMarker_LastStatement,
      //
      DeeplyNestedInVariousScopes1,
      DeeplyNestedInVariousScopes2,
      InsideCaseClausesLast,
      InsideCaseClausesNonLast
    )

    val AllContexts_TopLevel = Seq(
      TopLevel,
      TopLevel_LastStatement,
      TopLevel_LastStatement_NoNewLineAtEOF
    )
    val AllContexts_WithBracesOrEndMarkerAtDeepestPosition = Seq(
      ClassWithBraces,
      ClassWithBraces_LastStatement,
      ClassWithColonAndEndMarker,
      ClassWithColonAndEndMarker_LastStatement,
      //
      NestedClassWithBraces,
      NestedClassWithBraces_LastStatement,
      NestedClassWithColonAndEndMarker,
      NestedClassWithColonAndEndMarker_LastStatement,
    )
  }

  object IndentedBlockContexts {
    private def contexts(seq: String*): Seq[String] = seq.map(_.trim)

    val AfterAssignOrArrowSign: Seq[String] = contexts(
      // definitions
      s"""def test = $Caret""",
      s"""val test = $Caret""",
      s"""var test = $Caret""",
      s"""class A {
         |  def this() = $Caret
         |}""".stripMargin,

      // assigment
      s"""obj.id = $Caret""",
      s"""map(key) = $Caret""",

      s"given stringParser: StringParser[String] = $Caret",

      // function literal & context function literal
      s"""val test = (param: Int) => $Caret""",
      s"""val test = (param: Int) ?=> $Caret""",

      // argument blocks
      s"""val test = x.foreach: $Caret""",
      s"""val test = x.foreach: a => $Caret""",

      /** case clause body
       * (test data for typing in single clause body
       * typing of multiple case clauses are tested separately
       * see [[Scala3TestDataCaseClausesEditorStates]])
       */
      s"""outer match
         |  case lastClause => $Caret
         |""".stripMargin,
      s"""outer match
         |  case middleClause => $Caret
         |  case _ =>
         |""".stripMargin,
    )

    private val ForEnumeratorsWithBraces = contexts(
      // with braces
      // generator
      s"""for {
         |  last <- $Caret
         |} yield 42""".stripMargin,
      s"""for {
         |  nonLast <- $Caret
         |  x = 42
         |} yield 42""".stripMargin,
      // pattern def
      s"""for {
         |  a <- 1 to 2
         |  last = $Caret
         |} yield 42""".stripMargin,
      s"""for {
         |  a <- 1 to 2
         |  nonLast = $Caret
         |  b <- 1 to 2
         |} yield 42""".stripMargin,
    )
    private val ForEnumeratorsBraceless: Seq[String] =
      ForEnumeratorsWithBraces
        .map(_.replace("for {", "for").replace("} yield", "yield"))

    val ForEnumeratorsAll: Seq[String] =
      ForEnumeratorsWithBraces ++
        ForEnumeratorsBraceless

    val ControlFlow: Seq[String] = contexts(
      // if/then/else
      s"""if (true) $Caret
         |""".stripMargin,
      s"""if (true) then $Caret
         |""".stripMargin,
      s"""if 2 + 2 == 42 then $Caret
         |""".stripMargin,
      s"""if true then $Caret
         |else 42
         |""".stripMargin,
      s"""if true then 42
         |else $Caret
         |""".stripMargin,

      // for + do/yield/-
      s"""for (x <- 1 to 3) $Caret""",
      s"""for (x <- 1 to 3) do $Caret""",
      s"""for x <- 1 to 3 do $Caret""",
      s"""for (x <- 1 to 3) yield $Caret""",
      // for + do/yield/- (multiline enumerators)
      s"""for {
         |  x <- 1 to 3
         |} $Caret""".stripMargin,
      s"""for {
         |  x <- 1 to 3
         |} do $Caret""".stripMargin,
      s"""for {
         |  x <- 1 to 3
         |} yield $Caret""".stripMargin,
      // for + do/yield/- (multiline enumerators, braceless for)
      s"""for
         |  x <- 1 to 3
         |do $Caret""".stripMargin,
      s"""for
         |  x <- 1 to 3
         |yield $Caret""".stripMargin,

      // while + do/-
      s"""while (2 * 2 == 5) $Caret""",
      s"""while (2 * 2 == 5) do $Caret""",
      s"""while 2 * 2 == 5 do $Caret""",

      /** try / finally (catch is handled in [[Scala3TestDataCaseClausesEditorStates]]) */
      s"""try $Caret
         |catch {
         |  case _ =>
         |}
         |""".stripMargin,
      s"""try 42
         |finally $Caret
         |  ???""".stripMargin,

      // throw
      s"""throw $Caret"""
    )

    val Extensions: Seq[String] = contexts(
      s"""extension (x: String) $Caret""",
      s"""extension (x: String) $Caret
         |end extension""".stripMargin,
      s"""extension (x: String) {$Caret
         |}""".stripMargin,
    )

    val TemplateDefinitions: Seq[String] = contexts(
      s"""class A: $Caret""",
      s"""trait A: $Caret""",
      s"""object A: $Caret""",
      // with end marker
      s"""class A: $Caret
         |end A""".stripMargin,
      s"""trait A: $Caret
         |end A""".stripMargin,
      s"""object A: $Caret
         |end A""".stripMargin,
      // with braces
      s"""class A { $Caret
         |}""".stripMargin,
      s"""trait A { $Caret
         |}""".stripMargin,
      s"""object A { $Caret
         |}""".stripMargin,
    )

    val GivenWith: Seq[String] = contexts(
      s"""given intOrd: Ord[Int] with $Caret""",
      s"""given intOrd: Ord[Int] with $Caret
         |end intOrd""".stripMargin,
      s"""given intOrd: Ord[Int] with {$Caret
         |}""".stripMargin,
    )

    val AllContextsAcceptingStatements: Seq[String] =
      AfterAssignOrArrowSign ++
        ControlFlow ++
        TemplateDefinitions ++
        ForEnumeratorsAll ++
        GivenWith
  }

  //noinspection TypeAnnotation
  object CodeToType {
    val BlankLines = CodeWithDebugName(
      """
        |
        |
        |""".stripMargin,
      "BlankLines"
    )

    val BlockStatements =
      CodeWithDebugName(
        """
          |var x = 1
          |var y = 2
          |
          |
          |//line comment
          |x + y""".stripMargin,
        "Statements"
      )

    val BlockExpressions =
      CodeWithDebugName(
        """
          |println(1)
          |println(2)
          |
          |
          |//line comment
          |x + y""".stripMargin,
        "Expressions"
      )

    val CaseClauses =
      CodeWithDebugName(
        """
          |case _: A =>
          |case _: B =>
          |
          |
          |/* block comment */
          |case _: C =>""".stripMargin,
        "CaseClauses"
      )

    val DefDef = CodeWithDebugName(
      """
        |def f1 = ???
        |def f2 = ???
        |
        |
        |/** doc comment */
        |def f3 = ???""".stripMargin,
      "DefDef"
    )

    /**
     * TODO: add annotations between some statements when these two issues are fixed: SCL-18844 and SCL-18846
     * {{{
     *   @nowarn
     *   @deprecated
     *   def f1 = ???
     * }}}
     */
    val TemplateStat = CodeWithDebugName(
      """
        |trait T
        |object O
        |class C
        |
        |
        |//line comment
        |sealed class SC
        |
        |import scala.collection._
        |
        |println("just expression")""".stripMargin,
      "TemplateStat"
    )
  }
}
