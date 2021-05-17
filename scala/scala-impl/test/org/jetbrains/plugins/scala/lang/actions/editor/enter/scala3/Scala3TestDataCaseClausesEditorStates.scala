package org.jetbrains.plugins.scala.lang.actions.editor.enter.scala3

import com.intellij.testFramework.EditorTestUtil

private object Scala3TestDataCaseClausesEditorStates {

  private val Caret = EditorTestUtil.CARET_TAG
  private val MatchStart = "42 match"
  private val MatchWithBracesStart = "42 match {"
  private val MatchWithBracesEnd = "\n}"
  private val TryCatchStart = "try 42\ncatch"

  private val MatchCaseClauses_EmptyBody: EditorStates = EditorStates("MatchCaseClausesWithEmptyBodyStates", Seq((
    s"""42 match $Caret
       |""".stripMargin,
    TypeText.Enter
  ), (
    s"""42 match
       |  $Caret
       |""".stripMargin,
    TypeText("case _: A => ")
  ), (
    s"""42 match
       |  case _: A => $Caret
       |""".stripMargin,
    TypeText.Enter
  ), (
    s"""42 match
       |  case _: A =>
       |    $Caret
       |""".stripMargin,
    TypeText("case _: B =>")
  ), (
    s"""42 match
       |  case _: A =>
       |  case _: B =>$Caret
       |""".stripMargin,
    TypeText("\n\n")
  ), (
    s"""42 match
       |  case _: A =>
       |  case _: B =>
       |
       |    $Caret
       |""".stripMargin,
    TypeText("case _: C => ")
  ), (
    s"""42 match
       |  case _: A =>
       |  case _: B =>
       |
       |  case _: C => $Caret
       |""".stripMargin,
    TypeText("\n\n\n")
  ), (
    s"""42 match
       |  case _: A =>
       |  case _: B =>
       |
       |  case _: C =>
       |
       |
       |    $Caret
       |""".stripMargin,
    TypeText("case _: D =>")
  ), (
    s"""42 match
       |  case _: A =>
       |  case _: B =>
       |
       |  case _: C =>
       |
       |
       |  case _: D =>$Caret
       |""".stripMargin,
    TypeText("case _: D =>")
  )))

  private val MatchCaseClauses_BodyWithCode: EditorStates = EditorStates("MatchCaseClausesWithNonEmptyBodyStates", Seq((
    s"""42 match $Caret
       |""".stripMargin,
    TypeText.Enter
  ), (
    s"""42 match
       |  $Caret
       |""".stripMargin,
    TypeText("""case _: A => println("a")""")
  ), (
    s"""42 match
       |  case _: A => println("a")$Caret
       |""".stripMargin,
    TypeText.Enter
  ), (
    s"""42 match
       |  case _: A => println("a")
       |  $Caret
       |""".stripMargin,
    TypeText("case _: B =>")
  ), (
    s"""42 match
       |  case _: A => println("a")
       |  case _: B =>$Caret
       |""".stripMargin,
    TypeText.Enter
  ), (
    s"""42 match
       |  case _: A => println("a")
       |  case _: B =>
       |    $Caret
       |""".stripMargin,
    TypeText(s"""println("b1")\nprintln("b2")\n""")
  ), (
    s"""42 match
       |  case _: A => println("a")
       |  case _: B =>
       |    println("b1")
       |    println("b2")
       |    $Caret
       |""".stripMargin,
    TypeText("\n\n")
  ), (
    s"""42 match
       |  case _: A => println("a")
       |  case _: B =>
       |    println("b1")
       |    println("b2")
       |
       |
       |    $Caret
       |""".stripMargin,
    TypeText("case _: C =>\n")
  ), (
    s"""42 match
       |  case _: A => println("a")
       |  case _: B =>
       |    println("b1")
       |    println("b2")
       |
       |
       |  case _: C =>
       |    $Caret
       |""".stripMargin,
    TypeText("")
  )))

  private val MatchCaseClauses_WithRedundantSpaces_EmptyBody_NonLastCaseClause_Mix: Seq[EditorStates] = Seq(
    EditorStates(
      "MatchCaseClauses WithRedundantSpacesBeforeBody EmptyBody NonLastCaseClause",
      s"""42 match
         |  case 1 =>     $Caret
         |  case 2 =>
         |""".stripMargin,
      TypeText.Enter,
      s"""42 match
         |  case 1 =>
         |    $Caret
         |  case 2 =>
         |""".stripMargin
    ),
    EditorStates(
      "MatchCaseClauses WithRedundantSpacesAroundBody EmptyBody NonLastCaseClause",
      s"""42 match
         |  case 1 =>  $Caret    ${""}
         |  case 2 =>
         |""".stripMargin,
      TypeText.Enter,
      s"""42 match
         |  case 1 =>
         |    $Caret
         |  case 2 =>
         |""".stripMargin
    ),
    EditorStates(
      "MatchCaseClauses WithRedundantSpacesAfterBody EmptyBody NonLastCaseClause",
      s"""42 match
         |  case 1 =>$Caret    ${""}
         |  case 2 =>
         |""".stripMargin,
      TypeText.Enter,
      s"""42 match
         |  case 1 =>
         |    $Caret
         |  case 2 =>
         |""".stripMargin
    )
  )

  private val MatchCaseClauses_WithRedundantSpaces_EmptyBody_LastCaseClause_Mix: Seq[EditorStates] = Seq(
    EditorStates(
      "MatchCaseClauses WithRedundantSpacesBeforeBody EmptyBody LastCaseClause",
      s"""42 match
         |  case 1 =>     $Caret
         |""".stripMargin,
      TypeText.Enter,
      s"""42 match
         |  case 1 =>
         |    $Caret
         |""".stripMargin
    ),
    EditorStates(
      "MatchCaseClauses WithRedundantSpacesAroundBody EmptyBody LastCaseClause",
      s"""42 match
         |  case 1 =>  $Caret    ${""}
         |""".stripMargin,
      TypeText.Enter,
      s"""42 match
         |  case 1 =>
         |    $Caret
         |""".stripMargin
    ),
    EditorStates(
      "MatchCaseClauses WithRedundantSpacesAfterBody EmptyBody LastCaseClause",
      s"""42 match
         |  case 1 =>$Caret    ${""}
         |""".stripMargin,
      TypeText.Enter,
      s"""42 match
         |  case 1 =>
         |    $Caret
         |""".stripMargin
    ),
  )

  private val MatchCaseClauses_WithRedundantSpaces_BodyWithCode_NonLastLastCaseClause_Mix: Seq[EditorStates] = Seq(
    EditorStates(
      "MatchCaseClauses WithRedundantSpacesBeforeArrow WithCode NonLastCaseClause 1",
      s"""42 match
         |  case 1 =>     ${Caret}42
         |""".stripMargin,
      TypeText.Enter,
      s"""42 match
         |  case 1 =>
         |    ${Caret}42
         |""".stripMargin
    ),
    EditorStates(
      "MatchCaseClauses WithRedundantSpacesBeforeArrow WithCode NonLastCaseClause 2",
      s"""42 match
         |  case 1 =>     ${Caret}1 + 2 + 3
         |""".stripMargin,
      TypeText.Enter,
      s"""42 match
         |  case 1 =>
         |    ${Caret}1 + 2 + 3
         |""".stripMargin
    ),
    EditorStates(
      "MatchCaseClauses WithRedundantSpacesBeforeArrow WithCode NonLastCaseClause 3",
      s"""42 match
         |  case 1 =>     $Caret
         |    42
         |  case 2 =>
         |""".stripMargin,
      TypeText.Enter,
      s"""42 match
         |  case 1 =>
         |    $Caret
         |    42
         |  case 2 =>
         |""".stripMargin
    ),
    EditorStates(
      "MatchCaseClauses WithRedundantSpacesAroundArrow WithCode NonLastCaseClause 1",
      s"""42 match
         |  case 1 =>  $Caret    42
         |  case 2 =>
         |""".stripMargin,
      TypeText.Enter,
      s"""42 match
         |  case 1 =>
         |    ${Caret}42
         |  case 2 =>
         |""".stripMargin
    ),
    EditorStates(
      "MatchCaseClauses WithRedundantSpacesAroundArrow WithCode NonLastCaseClause 2",
      s"""42 match
         |  case 1 =>  $Caret    1 + 2 + 3
         |  case 2 =>
         |""".stripMargin,
      TypeText.Enter,
      s"""42 match
         |  case 1 =>
         |    ${Caret}1 + 2 + 3
         |  case 2 =>
         |""".stripMargin
    ),
    EditorStates(
      "MatchCaseClauses WithRedundantSpacesAroundArrow WithCode NonLastCaseClause 3",
      s"""42 match
         |  case 1 =>  $Caret    ${""}
         |    42
         |  case 2 =>
         |""".stripMargin,
      TypeText.Enter,
      s"""42 match
         |  case 1 =>
         |    $Caret
         |    42
         |  case 2 =>
         |""".stripMargin
    ),
    EditorStates(
      "MatchCaseClauses WithRedundantSpacesAfterArrow WithCode NonLastCaseClause 1",
      s"""42 match
         |  case 1 =>$Caret    42
         |  case 2 =>
         |""".stripMargin,
      TypeText.Enter,
      s"""42 match
         |  case 1 =>
         |    ${Caret}42
         |  case 2 =>
         |""".stripMargin
    ),
    EditorStates(
      "MatchCaseClauses WithRedundantSpacesAfterArrow WithCode NonLastCaseClause 2",
      s"""42 match
         |  case 1 =>$Caret    1 + 2 + 3
         |  case 2 =>
         |""".stripMargin,
      TypeText.Enter,
      s"""42 match
         |  case 1 =>
         |    ${Caret}1 + 2 + 3
         |  case 2 =>
         |""".stripMargin
    ),
    EditorStates(
      "MatchCaseClauses WithRedundantSpacesAfterArrow WithCode NonLastCaseClause 3",
      s"""42 match
         |  case 1 =>$Caret    ${""}
         |    42
         |  case 2 =>
         |""".stripMargin,
      TypeText.Enter,
      s"""42 match
         |  case 1 =>
         |    $Caret
         |    42
         |  case 2 =>
         |""".stripMargin
    )
  )

  private val MatchCaseClauses_WithRedundantSpaces_BodyWithCode_LastCaseClause_Mix: Seq[EditorStates] = Seq(
    EditorStates(
      "MatchCaseClauses WithRedundantSpacesBeforeArrow WithCode LastCaseClause 1",
      s"""42 match
         |  case 1 =>     ${Caret}42
         |""".stripMargin,
      TypeText.Enter,
      s"""42 match
         |  case 1 =>
         |    ${Caret}42
         |""".stripMargin
    ),
    EditorStates(
      "MatchCaseClauses WithRedundantSpacesBeforeArrow WithCode LastCaseClause 2",
      s"""42 match
         |  case 1 =>     ${Caret}1 + 2 + 3
         |""".stripMargin,
      TypeText.Enter,
      s"""42 match
         |  case 1 =>
         |    ${Caret}1 + 2 + 3
         |""".stripMargin
    ),
    EditorStates(
      "MatchCaseClauses WithRedundantSpacesBeforeArrow WithCode LastCaseClause 3",
      s"""42 match
         |  case 1 =>     $Caret
         |    42
         |""".stripMargin,
      TypeText.Enter,
      s"""42 match
         |  case 1 =>
         |    $Caret
         |    42
         |""".stripMargin
    ),
    EditorStates(
      "MatchCaseClauses WithRedundantSpacesAroundArrow WithCode LastCaseClause 1",
      s"""42 match
         |  case 1 =>  $Caret    42
         |""".stripMargin,
      TypeText.Enter,
      s"""42 match
         |  case 1 =>
         |    ${Caret}42
         |""".stripMargin
    ),
    EditorStates(
      "MatchCaseClauses WithRedundantSpacesAroundArrow WithCode LastCaseClause 2",
      s"""42 match
         |  case 1 =>  $Caret    1 + 2 + 3
         |""".stripMargin,
      TypeText.Enter,
      s"""42 match
         |  case 1 =>
         |    ${Caret}1 + 2 + 3
         |""".stripMargin
    ),
    EditorStates(
      "MatchCaseClauses WithRedundantSpacesAroundArrow WithCode LastCaseClause 3",
      s"""42 match
         |  case 1 =>  $Caret    ${""}
         |    42
         |""".stripMargin,
      TypeText.Enter,
      s"""42 match
         |  case 1 =>
         |    $Caret
         |    42
         |""".stripMargin
    ),
    EditorStates(
      "MatchCaseClauses WithRedundantSpacesAfterArrow WithCode LastCaseClause 1",
      s"""42 match
         |  case 1 =>$Caret    42
         |""".stripMargin,
      TypeText.Enter,
      s"""42 match
         |  case 1 =>
         |    ${Caret}42
         |""".stripMargin
    ),
    EditorStates(
      "MatchCaseClauses WithRedundantSpacesAfterArrow WithCode LastCaseClause 2",
      s"""42 match
         |  case 1 =>$Caret    1 + 2 + 3
         |""".stripMargin,
      TypeText.Enter,
      s"""42 match
         |  case 1 =>
         |    ${Caret}1 + 2 + 3
         |""".stripMargin
    ),
    EditorStates(
      "MatchCaseClauses WithRedundantSpacesAfterArrow WithCode LastCaseClause 3",
      s"""42 match
         |  case 1 =>$Caret    ${""}
         |    42
         |""".stripMargin,
      TypeText.Enter,
      s"""42 match
         |  case 1 =>
         |    $Caret
         |    42
         |""".stripMargin
    )
  )

  private val MatchCaseClausesAll_Braceless: Seq[EditorStates] =
    MatchCaseClauses_EmptyBody +:
      MatchCaseClauses_BodyWithCode +:
      MatchCaseClauses_WithRedundantSpaces_EmptyBody_NonLastCaseClause_Mix ++:
      MatchCaseClauses_WithRedundantSpaces_EmptyBody_LastCaseClause_Mix ++:
      MatchCaseClauses_WithRedundantSpaces_BodyWithCode_LastCaseClause_Mix ++:
      MatchCaseClauses_WithRedundantSpaces_BodyWithCode_NonLastLastCaseClause_Mix

  val MatchCaseClausesAll: Seq[EditorStates] =
    MatchCaseClausesAll_Braceless

  val MatchCaseClausesAll_WithBraces: Seq[EditorStates] =
    MatchCaseClausesAll_Braceless.map { editorStates: EditorStates =>
      val statesNew = editorStates.states.map(_.withTransformedText(text => {
        text.replace(MatchStart, MatchWithBracesStart) + MatchWithBracesEnd
      }))
      EditorStates(debugName = editorStates.debugName.getOrElse("unnamed") + " (with braces)", statesNew)
    }

  private def toTryCatchEditorState(state: EditorState): EditorState =
    state.withTransformedText(_.replace(MatchStart, TryCatchStart).ensuring(_.contains(TryCatchStart)))

  private val TryCatchCaseClausesWithEmptyBody: EditorStates = EditorStates(
    """TryCatchCaseClausesWithEmptyBody""",
    MatchCaseClauses_EmptyBody.states.map(toTryCatchEditorState)
  )

  private val TryCatchCaseClausesWithNonEmptyBody: EditorStates = EditorStates(
    """TryCatchCaseClausesWithNonEmptyBody""",
    MatchCaseClauses_BodyWithCode.states.map(toTryCatchEditorState)
  )

  val TryCatchCaseClausesAll: Seq[EditorStates] =
    TryCatchCaseClausesWithEmptyBody +: TryCatchCaseClausesWithNonEmptyBody +: Nil
}
