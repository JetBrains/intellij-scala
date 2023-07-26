package org.jetbrains.plugins.scala.lang.findUsages

import com.intellij.find.findUsages.FindUsagesOptions
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.findUsages.factory.ScalaTypeDefinitionFindUsagesOptions

class FindUsagesTest_Scala2 extends FindUsagesTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  private def classWithMembersOptions: FindUsagesOptions = {
    val options = new ScalaTypeDefinitionFindUsagesOptions(getProject)
    options.isMembersUsages = true
    options
  }

  def testFindObjectWithMembers(): Unit = {
    doTest(
      s"""object ${CARET}Test {
         |  def foo() = ???
         |
         |  ${start(1)}Test${end(1)}.${start(0)}foo${end(0)}
         |}
         |""".stripMargin,
      classWithMembersOptions)
  }

  def testFindValOverriders(): Unit = {
    doTest(
      s"""
         |trait FindMyMembers {
         |  val findMyVal$CARET: Int
         |  def methodInTrait(): Unit = {
         |    println(${start(0)}findMyVal${end(0)})
         |  }
         |}
         |
         |class FindMyMembersImpl extends FindMyMembers {
         |  override val findMyVal: Int = 1
         |
         |  def methodInImpl(): Unit = {
         |    println(${start(1)}findMyVal${end(1)})
         |  }
         |}
      """.stripMargin)
  }

  def testFindDefOverriders(): Unit = {
    doTest(
      s"""
         |trait FindMyMembers {
         |  def findMyDef$CARET: Int
         |  def methodInTrait(): Unit = {
         |    println(${start(0)}findMyDef${end(0)})
         |  }
         |}
         |
         |class FindMyMembersImpl extends FindMyMembers {
         |  override def findMyDef: Int = 1
         |
         |  def methodInImpl(): Unit = {
         |    println(${start(1)}findMyDef${end(1)})
         |  }
         |}
         |
         |class FindMyMembersImpl2 extends FindMyMembers {
         |  override val findMyDef: Int = 2
         |
         |  def methodInImpl2(): Unit = {
         |    println(${start(2)}findMyDef${end(2)})
         |  }
         |}
         |
         |class FindMyMembersImpl3 extends FindMyMembers {
         |  override var findMyDef: Int = 2
         |
         |  def methodInImpl2(): Unit = {
         |    ${start(3)}findMyDef${end(3)} = 3
         |    println(${start(4)}findMyDef${end(4)})
         |  }
         |}
      """.stripMargin)
  }

  def testUnaryOperator(): Unit = doTest(
    s"""class B {
       |  def ${CARET}unary_! : B = this
       |}
       |
       |object Test {
       |  val b = new B
       |  ${start(0)}!${end(0)}b
       |  b.${start(1)}unary_!${end(1)}
       |  b.${start(2)}unary_$$bang${end(2)}
       |}
       |""".stripMargin)

}
