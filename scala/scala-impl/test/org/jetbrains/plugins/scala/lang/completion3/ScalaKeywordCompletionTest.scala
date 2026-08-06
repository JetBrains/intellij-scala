package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion, WithIndexingMode}
import org.junit.Test

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
class ScalaKeywordCompletionTest extends ScalaCompletionTestBase {

  @Test
  def testPrivateVal(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  private va$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  private val $CARET
         |}
      """.stripMargin,
    item = "val"
  )

  @Test
  def testPrivateThis(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  pr$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  private[$CARET]
         |}
      """.stripMargin,
    item = "private",
    char = '['
  )

  @Test
  def testFirstVal(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  def foo() {
         |    va${CARET}vv.v
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  def foo() {
         |    val ${CARET}vv.v
         |  }
         |}
      """.stripMargin,
    item = "val",
    char = ' '
  )

  @Test
  def testIfAfterCase(): Unit = doCompletionTest(
    fileText =
      s"""
         |1 match {
         |  case a if$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |1 match {
         |  case a if $CARET
         |}
      """.stripMargin,
    item = "if",
    char = ' '
  )

  @Test
  def testValUnderCaseClause(): Unit = doCompletionTest(fileText =
    s"""
       |1 match {
       |  case 1 =>
       |    val$CARET
       |}
      """.stripMargin,
    resultText =
      s"""
         |1 match {
         |  case 1 =>
         |    val $CARET
         |}
      """.stripMargin,
    item = "val",
    char = ' '
  )

  @Test
  def testDefUnderCaseClause(): Unit = doCompletionTest(
    fileText =
      s"""
         |1 match {
         |  case 1 =>
         |    def$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |1 match {
         |  case 1 =>
         |    def $CARET
         |}
      """.stripMargin,
    item = "def",
    char = ' '
  )

  @Test
  def testIfParentheses(): Unit = doCompletionTest(
    fileText =
      s"""
         |1 match {
         |  case 1 =>
         |    if$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |1 match {
         |  case 1 =>
         |    if ($CARET)
         |}
      """.stripMargin,
    item = "if",
    char = '('
  )

  @Test
  def testTryBraces(): Unit = doCompletionTest(
    fileText =
      s"""
         |1 match {
         |  case 1 =>
         |    try$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |1 match {
         |  case 1 =>
         |    try {$CARET}
         |}
      """.stripMargin,
    item = "try",
    char = '{'
  )

  @Test
  def testDoWhile(): Unit = doCompletionTest(
    fileText =
      s"""
         |do {} whi$CARET
         |1
      """.stripMargin,
    resultText =
      s"""
         |do {} while ($CARET)
         |1
      """.stripMargin,
    item = "while",
    char = '('
  )

  //region extends
  @Test
  def testExtendsAsLastInFile(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Test e$CARET
         |""".stripMargin,
    resultText =
      s"""
         |class Test extends $CARET
         |""".stripMargin,
    item = "extends"
  )

  @Test
  def testExtendsOnANewLine(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Test
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""
         |class Test
         |extends $CARET
         |""".stripMargin,
    item = "extends"
  )

  @Test
  def testExtendsAfterBlockComment(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Test /*comment*/ e$CARET
         |""".stripMargin,
    resultText =
      s"""
         |class Test /*comment*/ extends $CARET
         |""".stripMargin,
    item = "extends"
  )

  @Test
  def testExtendsAfterLineComment(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Test // comment
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""
         |class Test // comment
         |extends $CARET
         |""".stripMargin,
    item = "extends"
  )

  @Test
  def testExtendsBeforeSemicolon(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Test e$CARET;
         |""".stripMargin,
    resultText =
      s"""
         |class Test extends $CARET;
         |""".stripMargin,
    item = "extends"
  )

  // SCL-19181
  @Test
  def testExtendsBeforeId(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Test e$CARET Base
         |""".stripMargin,
    resultText =
      s"""
         |class Test extends ${CARET}Base
         |""".stripMargin,
    item = "extends"
  )

  @Test
  def testExtendsBetweenClasses(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Test e$CARET
         |class Test2
         |""".stripMargin,
    resultText =
      s"""
         |class Test extends $CARET
         |class Test2
         |""".stripMargin,
    item = "extends"
  )

  // This one is highly opinionated
  @Test
  def testExtendsBetweenClasses2(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Test
         |e${CARET}class Test2
         |""".stripMargin,
    resultText =
      s"""
         |class Test
         |extends ${CARET}class Test2
         |""".stripMargin,
    item = "extends",
    char = Lookup.NORMAL_SELECT_CHAR
  )

  @Test
  def testExtendsBetweenClasses3(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |class Test
         |
         |e${CARET}class Test2
         |""".stripMargin,
    item = "extends"
  )

  // SCL-19022
  @Test
  def testExtendsBeforeBody(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Test e$CARET {
         |}
         |""".stripMargin,
    resultText =
      s"""
         |class Test extends $CARET{
         |}
         |""".stripMargin,
    item = "extends"
  )

  @Test
  def testExtendsBeforeObjectBody(): Unit = doCompletionTest(
    fileText =
      s"""
         |object Test e$CARET {
         |}
         |""".stripMargin,
    resultText =
      s"""
         |object Test extends $CARET{
         |}
         |""".stripMargin,
    item = "extends"
  )

  @Test
  def testExtendsBeforeExtends(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |object Obj e$CARET extends
         |""".stripMargin,
    item = "extends"
  )

  @Test
  def testExtendsBeforeExtendsWithComment(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |object Obj e$CARET /*comment*/ extends
         |""".stripMargin,
    item = "extends"
  )

  @Test
  def testExtendsAfterExtends(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |object Obj extends e$CARET
         |""".stripMargin,
    item = "extends"
  )

  @Test
  def testExtendsAfterExtendsWithComment(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |object Obj extends /*comment*/ e$CARET
         |""".stripMargin,
    item = "extends"
  )
  //endregion

  //region with
  @Test
  def testWithAsLastInFile(): Unit = doCompletionTest(
    fileText =
      s"""
         |trait A
         |class Test extends A w$CARET
         |""".stripMargin,
    resultText =
      s"""
         |trait A
         |class Test extends A with $CARET
         |""".stripMargin,
    item = "with"
  )

  @Test
  def testWithOnANewLine(): Unit = doCompletionTest(
    fileText =
      s"""
         |trait A
         |class Test extends A
         |w$CARET
         |""".stripMargin,
    resultText =
      s"""
         |trait A
         |class Test extends A
         |with $CARET
         |""".stripMargin,
    item = "with"
  )

  @Test
  def testWithAfterBlockComment(): Unit = doCompletionTest(
    fileText =
      s"""
         |trait A
         |class Test extends A /*comment*/ w$CARET
         |""".stripMargin,
    resultText =
      s"""
         |trait A
         |class Test extends A /*comment*/ with $CARET
         |""".stripMargin,
    item = "with"
  )

  @Test
  def testWithAfterBlockComment2(): Unit = doCompletionTest(
    fileText =
      s"""
         |trait A
         |class Test extends A
         |/*comment*/ w$CARET
         |""".stripMargin,
    resultText =
      s"""
         |trait A
         |class Test extends A
         |/*comment*/ with $CARET
         |""".stripMargin,
    item = "with"
  )

  @Test
  def testWithAfterBlockComment3(): Unit = doCompletionTest(
    fileText =
      s"""
         |trait A
         |class Test extends A
         |/*comment*/
         |w$CARET
         |""".stripMargin,
    resultText =
      s"""
         |trait A
         |class Test extends A
         |/*comment*/
         |with $CARET
         |""".stripMargin,
    item = "with"
  )

  @Test
  def testWithAfterLineComment(): Unit = doCompletionTest(
    fileText =
      s"""
         |trait A
         |class Test extends A // comment
         |w$CARET
         |""".stripMargin,
    resultText =
      s"""
         |trait A
         |class Test extends A // comment
         |with $CARET
         |""".stripMargin,
    item = "with"
  )

  @Test
  def testWithAfterLineComment2(): Unit = doCompletionTest(
    fileText =
      s"""
         |trait A
         |class Test extends A
         |// comment
         |w$CARET
         |""".stripMargin,
    resultText =
      s"""
         |trait A
         |class Test extends A
         |// comment
         |with $CARET
         |""".stripMargin,
    item = "with"
  )

  @Test
  def testWithBeforeSemicolon(): Unit = doCompletionTest(
    fileText =
      s"""
         |trait A
         |class Test extends A w$CARET;
         |""".stripMargin,
    resultText =
      s"""
         |trait A
         |class Test extends A with $CARET;
         |""".stripMargin,
    item = "with"
  )

  @Test
  def testWithBeforeId(): Unit = doCompletionTest(
    fileText =
      s"""
         |trait A
         |trait B
         |class Test extends A w$CARET B
         |""".stripMargin,
    resultText =
      s"""
         |trait A
         |trait B
         |class Test extends A with ${CARET}B
         |""".stripMargin,
    item = "with"
  )

  @Test
  def testWithBetweenClasses(): Unit = doCompletionTest(
    fileText =
      s"""
         |trait A
         |class Test extends A w$CARET
         |class Test2
         |""".stripMargin,
    resultText =
      s"""
         |trait A
         |class Test extends A with $CARET
         |class Test2
         |""".stripMargin,
    item = "with"
  )

  @Test
  def testWithBeforeBody(): Unit = doCompletionTest(
    fileText =
      s"""
         |trait A
         |class Test extends A w$CARET {
         |}
         |""".stripMargin,
    resultText =
      s"""
         |trait A
         |class Test extends A with $CARET{
         |}
         |""".stripMargin,
    item = "with"
  )

  @Test
  def testWithBeforeWith(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |trait A
         |object Obj extends A w$CARET with
         |""".stripMargin,
    item = "with"
  )

  @Test
  def testWithBeforeWithWithComment(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |trait A
         |object Obj extends A w$CARET /*comment*/ with
         |""".stripMargin,
    item = "with"
  )

  @Test
  def testWithAfterTwoNewlines(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |trait A
         |object Obj extends A
         |
         |w$CARET
         |""".stripMargin,
    item = "with"
  )

  @Test
  def testWithAfterTwoNewlinesAndComment(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |trait A
         |object Obj extends A
         |
         |//comment
         |w$CARET
         |""".stripMargin,
    item = "with"
  )

  @Test
  def testWithAfterTwoNewlinesAndComment2(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |trait A
         |object Obj extends A
         |//comment
         |
         |w$CARET
         |""".stripMargin,
    item = "with"
  )

  @Test
  def testWithAfterWith(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |trait A
         |object Obj extends A with w$CARET
         |""".stripMargin,
    item = "with"
  )

  @Test
  def testWithAfterWithWithComment(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |trait A
         |object Obj extends A with /*comment*/ w$CARET
         |""".stripMargin,
    item = "with"
  )

  @Test
  def testNoWithOnANewLine(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |trait A
         |w$CARET
         |""".stripMargin,
    item = "with"
  )

  @Test
  def testNoWithOnANewLine2(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |def foo = 2
         |w$CARET
         |""".stripMargin,
    item = "with"
  )

  @Test
  def testNoWithOnANewLine3(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |w$CARET
         |""".stripMargin,
    item = "with"
  )
  //endregion

  //region fix-indent
  //region if-else
  @Test
  def else_doNotChangeWhitespacesOnTheSameLine(): Unit = doCompletionTest(
    fileText =
      s"""
         |def test(): Unit = {
         |  if (true) {}   $CARET
         |}
         |""".stripMargin,
    resultText =
      s"""
         |def test(): Unit = {
         |  if (true) {}   else $CARET
         |}
         |""".stripMargin,
    item = "else",
  )

  @Test
  def else_doNotChangeWhitespacesAfterBrace(): Unit = doCompletionTest(
    fileText =
      s"""
         |def test(): Unit = {
         |  if (true) {
         |  }   $CARET
         |}
         |""".stripMargin,
    resultText =
      s"""
         |def test(): Unit = {
         |  if (true) {
         |  }   else $CARET
         |}
         |""".stripMargin,
    item = "else",
  )

  @Test
  def else_fixIndent(): Unit = doCompletionTest(
    fileText =
      s"""
         |def test(): Unit = {
         |  if (true) {
         |  }
         |      $CARET
         |}
         |""".stripMargin,
    resultText =
      s"""
         |def test(): Unit = {
         |  if (true) {
         |  }
         |  else $CARET
         |}
         |""".stripMargin,
    item = "else",
  )

  @Test
  def else_fixIndent2(): Unit = doCompletionTest(
    fileText =
      s"""
         |def test(): Unit = {
         |  if (true)
         |    println("yes")
         |    $CARET
         |}
         |""".stripMargin,
    resultText =
      s"""
         |def test(): Unit = {
         |  if (true)
         |    println("yes")
         |  else $CARET
         |}
         |""".stripMargin,
    item = "else",
  )

  @Test
  def else_fixIndent3(): Unit = doCompletionTest(
    fileText =
      s"""
         |def test(): Unit = {
         |  if (true)
         |    println("yes")
         |$CARET
         |}
         |""".stripMargin,
    resultText =
      s"""
         |def test(): Unit = {
         |  if (true)
         |    println("yes")
         |  else $CARET
         |}
         |""".stripMargin,
    item = "else",
  )
  //endregion

  //region match-case
  @Test
  def matchCase_fixIndent(): Unit = doCompletionTest(
    fileText =
      s"""
         |def test(): Unit = {
         |  5 match {
         |    case 1 => 2
         |      $CARET
         |  }
         |}
         |""".stripMargin,
    resultText =
      s"""
         |def test(): Unit = {
         |  5 match {
         |    case 1 => 2
         |    case $CARET
         |  }
         |}
         |""".stripMargin,
    item = "case",
  )

  @Test
  def matchCase_fixIndent2(): Unit = doCompletionTest(
    fileText =
      s"""
         |def test(): Unit = {
         |  5 match {
         |    case 1 => 2
         |$CARET
         |  }
         |}
         |""".stripMargin,
    resultText =
      s"""
         |def test(): Unit = {
         |  5 match {
         |    case 1 => 2
         |    case $CARET
         |  }
         |}
         |""".stripMargin,
    item = "case",
  )
  //endregion

  //region try-finally
  @Test
  def tryFinally_doNotChangeWhitespacesOnTheSameLine(): Unit = doCompletionTest(
    fileText =
      s"""
         |def test(): Unit = {
         |  try {}   $CARET
         |}
         |""".stripMargin,
    resultText =
      s"""
         |def test(): Unit = {
         |  try {}   finally $CARET
         |}
         |""".stripMargin,
    item = "finally",
  )

  @Test
  def tryFinally_doNotChangeWhitespacesAfterBrace(): Unit = doCompletionTest(
    fileText =
      s"""
         |def test(): Unit = {
         |  try {
         |  }   $CARET
         |}
         |""".stripMargin,
    resultText =
      s"""
         |def test(): Unit = {
         |  try {
         |  }   finally $CARET
         |}
         |""".stripMargin,
    item = "finally",
  )

  @Test
  def tryFinally_fixIndent(): Unit = doCompletionTest(
    fileText =
      s"""
         |def test(): Unit = {
         |  try {
         |  }
         |      $CARET
         |}
         |""".stripMargin,
    resultText =
      s"""
         |def test(): Unit = {
         |  try {
         |  }
         |  finally $CARET
         |}
         |""".stripMargin,
    item = "finally",
  )

  @Test
  def tryFinally_fixIndent2(): Unit = doCompletionTest(
    fileText =
      s"""
         |def test(): Unit = {
         |  try
         |    println("yes")
         |$CARET
         |}
         |""".stripMargin,
    resultText =
      s"""
         |def test(): Unit = {
         |  try
         |    println("yes")
         |  finally $CARET
         |}
         |""".stripMargin,
    item = "finally",
  )
  //endregion
  //endregion
}

/** Version-specific tests */

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13
))
class ScalaKeywordCompletionTest_2_13 extends ScalaCompletionTestBase {
  @Test
  def testMatch(): Unit = doCompletionTest(
    fileText =
      s"42 m$CARET",
    resultText =
      s"""42 match {
         |  case $CARET
         |}""".stripMargin,
    item = "match"
  )

  @Test
  def testInfixMatch(): Unit = doCompletionTest(
    fileText =
      s"42 m$CARET ",
    resultText =
      s"""42 match {
         |  case $CARET
         |}""".stripMargin,
    item = "match"
  )

  //region try-catch
  @Test
  def testCatch(): Unit = doCompletionTest(
    fileText =
      s"try 42 c$CARET",
    resultText =
      s"""try 42 catch {
         |  case $CARET
         |}""".stripMargin,
    item = "catch"
  )

  @Test
  def tryCatch_doNotChangeWhitespacesOnTheSameLine(): Unit = doCompletionTest(
    fileText =
      s"""
         |def test(): Unit = {
         |  try {}   $CARET
         |}
         |""".stripMargin,
    resultText =
      s"""
         |def test(): Unit = {
         |  try {}   catch {
         |    case $CARET
         |  }
         |}
         |""".stripMargin,
    item = "catch",
  )

  @Test
  def tryCatch_doNotChangeWhitespacesAfterBrace(): Unit = doCompletionTest(
    fileText =
      s"""
         |def test(): Unit = {
         |  try {
         |  }   $CARET
         |}
         |""".stripMargin,
    resultText =
      s"""
         |def test(): Unit = {
         |  try {
         |  }   catch {
         |    case $CARET
         |  }
         |}
         |""".stripMargin,
    item = "catch",
  )

  @Test
  def tryCatch_fixIndent(): Unit = doCompletionTest(
    fileText =
      s"""
         |def test(): Unit = {
         |  try {
         |  }
         |      $CARET
         |}
         |""".stripMargin,
    resultText =
      s"""
         |def test(): Unit = {
         |  try {
         |  }
         |  catch {
         |    case $CARET
         |  }
         |}
         |""".stripMargin,
    item = "catch",
  )

  @Test
  def tryCatch_fixIndent2(): Unit = doCompletionTest(
    fileText =
      s"""
         |def test(): Unit = {
         |  try
         |    println("yes")
         |    $CARET
         |}
         |""".stripMargin,
    resultText =
      s"""
         |def test(): Unit = {
         |  try
         |    println("yes")
         |  catch {
         |    case $CARET
         |  }
         |}
         |""".stripMargin,
    item = "catch",
  )

  @Test
  def tryCatch_fixIndent3(): Unit = doCompletionTest(
    fileText =
      s"""
         |def test(): Unit = {
         |  try
         |    println("yes")
         |$CARET
         |}
         |""".stripMargin,
    resultText =
      s"""
         |def test(): Unit = {
         |  try
         |    println("yes")
         |  catch {
         |    case $CARET
         |  }
         |}
         |""".stripMargin,
    item = "catch",
  )
  //endregion
}

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_Latest
))
class ScalaKeywordCompletionTest_3_Latest extends ScalaCompletionTestBase {
  override protected def setUp(): Unit = {
    super.setUp()
    getScalaCodeStyleSettings.USE_SCALA3_INDENTATION_BASED_SYNTAX = true
  }

  @Test
  def testMatch(): Unit = doCompletionTest(
    fileText =
      s"42 m$CARET",
    resultText =
      s"""42 match
         |  case $CARET""".stripMargin,
    item = "match"
  )

  @Test
  def testMatchInBracelessBlock(): Unit = doCompletionTest(
    fileText =
      s"""object O:
         |  42 m$CARET
         |""".stripMargin,
    resultText =
      s"""object O:
         |  42 match
         |    case $CARET
         |""".stripMargin,
    item = "match"
  )

  @Test
  def testMatchInBracedBlock(): Unit = doCompletionTest(
    fileText =
      s"""object O {
         |  42 m$CARET
         |}""".stripMargin,
    resultText =
      s"""object O {
         |  42 match
         |    case $CARET
         |}""".stripMargin,
    item = "match"
  )

  @Test
  def testInfixMatch(): Unit = doCompletionTest(
    fileText =
      s"42 m$CARET ",
    resultText =
      s"""42 match
         |  case $CARET""".stripMargin,
    item = "match"
  )

  //region try-catch
  @Test
  def testCatch(): Unit = doCompletionTest(
    fileText =
      s"try 42 c$CARET",
    resultText =
      s"""try 42 catch
         |  case $CARET""".stripMargin,
    item = "catch"
  )

  @Test
  def testCatchInBracelessBlock(): Unit = doCompletionTest(
    fileText =
      s"""object O:
         |  try 42 c$CARET
         |""".stripMargin,
    resultText =
      s"""object O:
         |  try 42 catch
         |    case $CARET
         |""".stripMargin,
    item = "catch"
  )

  @Test
  def testCatchInBracedBlock(): Unit = doCompletionTest(
    fileText =
      s"""object O {
         |  try 42 c$CARET
         |}""".stripMargin,
    resultText =
      s"""object O {
         |  try 42 catch
         |    case $CARET
         |}""".stripMargin,
    item = "catch"
  )

  @Test
  def tryCatch_doNotChangeWhitespacesOnTheSameLine(): Unit = doCompletionTest(
    fileText =
      s"""
         |def test(): Unit = {
         |  try {}   $CARET
         |}
         |""".stripMargin,
    resultText =
      s"""
         |def test(): Unit = {
         |  try {}   catch
         |    case $CARET
         |}
         |""".stripMargin,
    item = "catch",
  )

  @Test
  def tryCatch_doNotChangeWhitespacesAfterBrace(): Unit = doCompletionTest(
    fileText =
      s"""
         |def test(): Unit = {
         |  try {
         |  }   $CARET
         |}
         |""".stripMargin,
    resultText =
      s"""
         |def test(): Unit = {
         |  try {
         |  }   catch
         |    case $CARET
         |}
         |""".stripMargin,
    item = "catch",
  )

  @Test
  def tryCatch_fixIndent(): Unit = doCompletionTest(
    fileText =
      s"""
         |def test(): Unit = {
         |  try {
         |  }
         |      $CARET
         |}
         |""".stripMargin,
    resultText =
      s"""
         |def test(): Unit = {
         |  try {
         |  }
         |  catch
         |    case $CARET
         |}
         |""".stripMargin,
    item = "catch",
  )

  @Test
  def tryCatch_fixIndent2(): Unit = doCompletionTest(
    fileText =
      s"""
         |def test(): Unit = {
         |  try
         |    println("yes")
         |$CARET
         |}
         |""".stripMargin,
    resultText =
      s"""
         |def test(): Unit = {
         |  try
         |    println("yes")
         |  catch
         |    case $CARET
         |}
         |""".stripMargin,
    item = "catch",
  )
  //endregion

  @Test
  def testWithInGivenDefinition(): Unit = doCompletionTest(
    fileText =
      s"""given foo: AnyRef w$CARET""".stripMargin,
    resultText =
      s"""given foo: AnyRef with $CARET""".stripMargin,
    item = "with"
  )

  @Test
  def testWithInGivenDefinitionWithBody(): Unit = doCompletionTest(
    fileText =
      s"""given foo: AnyRef w$CARET {
         |  val bar = 2
         |}""".stripMargin,
    resultText =
      s"""given foo: AnyRef with $CARET{
         |  val bar = 2
         |}""".stripMargin,
    item = "with"
  )
}
