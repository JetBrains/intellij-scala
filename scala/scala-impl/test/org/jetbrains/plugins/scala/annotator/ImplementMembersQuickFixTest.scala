package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.codeInspection.ScalaAnnotatorQuickFixTestBase

class ImplementMembersQuickFixTest extends ScalaAnnotatorQuickFixTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  //noinspection NotImplementedCode
  override protected def description: String = "...must either be declared abstract or implement abstract member..."

  override protected def descriptionMatches(s: String): Boolean =
    s != null && s.contains("must either be declared abstract or implement abstract member")

  def testProperDefaultValueForVariousMemberTypes(): Unit = {
    val before =
      s"""abstract class MyTrait {
         |  var myVariable: Int
         |  val myValue: Int
         |  def myDef: Int
         |  type MyType
         |}
         |
         |${START}class$CARET MyClass1 extends MyTrait$END
         |""".stripMargin

    val after =
      s"""abstract class MyTrait {
         |  var myVariable: Int
         |  val myValue: Int
         |  def myDef: Int
         |  type MyType
         |}
         |
         |class MyClass1 extends MyTrait {
         |  override var myVariable: Int = ${START}_$END
         |  override val myValue: Int = ???
         |
         |  override def myDef: Int = ???
         |
         |  override type MyType = this.type
         |}
         |""".stripMargin

    checkTextHasError(before)

    testQuickFix(before, after, "Implement members")
  }
}
