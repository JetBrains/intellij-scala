package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.lookup.Lookup
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith

class ScalaKeywordCompletionTest extends ScalaCompletionTestBase {

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

  /// extends

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

  def testExtendsBeforeExtends(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |object Obj e$CARET extends
         |""".stripMargin,
    item = "extends"
  )

  def testExtendsBeforeExtendsWithComment(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |object Obj e$CARET /*comment*/ extends
         |""".stripMargin,
    item = "extends"
  )

  def testExtendsAfterExtends(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |object Obj extends e$CARET
         |""".stripMargin,
    item = "extends"
  )

  def testExtendsAfterExtendsWithComment(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |object Obj extends /*comment*/ e$CARET
         |""".stripMargin,
    item = "extends"
  )

  /// with

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

  def testWithBeforeWith(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |trait A
         |object Obj extends A w$CARET with
         |""".stripMargin,
    item = "with"
  )

  def testWithBeforeWithWithComment(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |trait A
         |object Obj extends A w$CARET /*comment*/ with
         |""".stripMargin,
    item = "with"
  )

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

  def testWithAfterWith(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |trait A
         |object Obj extends A with w$CARET
         |""".stripMargin,
    item = "with"
  )

  def testWithAfterWithWithComment(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |trait A
         |object Obj extends A with /*comment*/ w$CARET
         |""".stripMargin,
    item = "with"
  )

  def testNoWithOnANewLine(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |trait A
         |w$CARET
         |""".stripMargin,
    item = "with"
  )

  def testNoWithOnANewLine2(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |def foo = 2
         |w$CARET
         |""".stripMargin,
    item = "with"
  )

  def testNoWithOnANewLine3(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |w$CARET
         |""".stripMargin,
    item = "with"
  )
}

/** Version specific tests */

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13
))
class ScalaKeywordCompletionTest_2_13 extends ScalaCompletionTestBase {
  def testMatch(): Unit = doCompletionTest(
    fileText =
      s"42 m$CARET",
    resultText =
      s"""42 match {
         |  case $CARET
         |}""".stripMargin,
    item = "match"
  )

  def testInfixMatch(): Unit = doCompletionTest(
    fileText =
      s"42 m$CARET ",
    resultText =
      s"""42 match {
         |  case $CARET
         |}""".stripMargin,
    item = "match"
  )

  def testCatch(): Unit = doCompletionTest(
    fileText =
      s"try 42 c$CARET",
    resultText =
      s"""try 42 catch {
         |  case $CARET
         |}""".stripMargin,
    item = "catch"
  )
}

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_Latest
))
class ScalaKeywordCompletionTest_3_Latest extends ScalaCompletionTestBase {
  def testMatch(): Unit = doCompletionTest(
    fileText =
      s"42 m$CARET",
    resultText =
      s"""42 match
         |  case $CARET""".stripMargin,
    item = "match"
  )

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

  def testInfixMatch(): Unit = doCompletionTest(
    fileText =
      s"42 m$CARET ",
    resultText =
      s"""42 match
         |  case $CARET""".stripMargin,
    item = "match"
  )

  def testCatch(): Unit = doCompletionTest(
    fileText =
      s"try 42 c$CARET",
    resultText =
      s"""try 42 catch
         |  case $CARET""".stripMargin,
    item = "catch"
  )

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

  def testWithInGivenDefinition(): Unit = doCompletionTest(
    fileText =
      s"""given foo: AnyRef w$CARET""".stripMargin,
    resultText =
      s"""given foo: AnyRef with $CARET""".stripMargin,
    item = "with"
  )

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
