package org.jetbrains.plugins.scala.highlighter.usages

import org.jetbrains.plugins.scala.ScalaVersion

class ScalaHighlightUsagesTest extends ScalaHighlightUsagesTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  def testCaseClass_NamedArgumentsInSyntheticMethods_CaretAtParameterDefinition(): Unit =
    doTest(
      s"""case class MyCaseClass(name: String, $CARET${start}age$end: Int)
        |MyCaseClass.apply(name = "42", ${start}age$end = 23)
        |val instance = MyCaseClass(name = "42", ${start}age$end = 23)
        |instance.copy(name = "23", ${start}age$end = 42)
        |""".stripMargin
    )

  def testCaseClass_NamedArgumentsInSyntheticMethods_CaretAtArgumentInApply(): Unit =
    doTest(
      s"""case class MyCaseClass(name: String, ${start}age$end: Int)
        |MyCaseClass.apply(name = "42", $CARET${start}age$end = 23)
        |val instance = MyCaseClass(name = "42", ${start}age$end = 23)
        |instance.copy(name = "23", ${start}age$end = 42)
        |""".stripMargin
    )

  def testCaseClass_NamedArgumentsInSyntheticMethods_CaretAtArgumentInApply_Explicit(): Unit =
    doTest(
      s"""case class MyCaseClass(name: String, ${start}age$end: Int)
        |MyCaseClass.apply(name = "42", ${start}age$end = 23)
        |val instance = MyCaseClass(name = "42", $CARET${start}age$end = 23)
        |instance.copy(name = "23", ${start}age$end = 42)
        |""".stripMargin
    )

  def testCaseClass_NamedArgumentsInSyntheticMethods_CaretAtArgumentInCopy_Explicit(): Unit =
    doTest(
      s"""case class MyCaseClass(name: String, age: Int)
        |MyCaseClass.apply(name = "42", age = 23)
        |val instance = MyCaseClass(name = "42", age = 23)
        |instance.copy(name = "23", $CARET${start}age$end = 42)
        |""".stripMargin
    )

  //SCL-20883
  def testSCL20883(): Unit =
    doTestWithDifferentCarets(
      s"""enum Color {
         |  case ${multiCaret(0)}${start}Red$end
         |}
         |object A {
         |  Color.${multiCaret(1)}${start}Red$end
         |}
         |""".stripMargin
    )

  def testSCL20883_CaseClassCase_CaretAtDefinition(): Unit =
    doTest(
      s"""enum Tree[+A] {
         |  case Leaf
         |  case $CARET${start}Node$end(value: A, r: Tree[A], l: Tree[A])
         |}
         |
         |object A {
         |  val n = println(Tree.${start}Node$end(1, Tree.Leaf, Tree.Leaf))
         |}
         |""".stripMargin
    )

  //TODO: patch test data once SCL-22942 is fixed
  def testSCL20883_CaseClassCase_CaretAtUsagePlace(): Unit =
    doTest(
      s"""enum Tree[+A] {
         |  case Leaf
         |  case ${start}Node$end(value: A, r: Tree[A], l: Tree[A])
         |}
         |
         |object A {
         |  val n = println(${start}Tree.${CARET}Node$end(1, Tree.Leaf, Tree.Leaf))
         |}
         |""".stripMargin
    )

  // SCL-21803
  def testGivenAlias(): Unit = doTest(
    s"""
       |trait Semigroup[T]
       |
       |given ${start}Semigroup[String]$end = null //given alias
       |
       |class usage {
       |  ${start}summon[Semigroup[String]]$end
       |
       |  ${start}given_${CARET}Semigroup_String$end
       |}
       |""".stripMargin
  )

  // SCL-21803
  def testGivenDefinition(): Unit = doTest(
    s"""
       |abstract class Semigroup[T](int: Int)
       |
       |given ${start}Semigroup[String]$end(1) with {} //given definition
       |
       |class usage {
       |  ${start}summon[Semigroup[String]]$end
       |
       |  ${start}given_${CARET}Semigroup_String$end
       |}
       |""".stripMargin
  )

  // SCL-21803
  def testGivenDefinitionWithNameId(): Unit = doTest(
    s"""
       |abstract class Semigroup[T](int: Int)
       |
       |given ${start}semigroup_String$end: Semigroup[String](1) with {} //given definition
       |
       |class usage {
       |  ${start}summon[Semigroup[String]]$end
       |
       |  ${start}semigroup${CARET}_String$end
       |}
       |""".stripMargin
  )
}
