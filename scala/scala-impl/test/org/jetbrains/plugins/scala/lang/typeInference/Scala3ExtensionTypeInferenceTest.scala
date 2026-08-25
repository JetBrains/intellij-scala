package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.ScalaVersion

class Scala3ExtensionTypeInferenceTest extends TypeInferenceTestBase {

  override protected def supportedIn(version: ScalaVersion) = version.isScala3

  def testSCL23491(): Unit = doTest(
    s"""
       |import scala.annotation.targetName
       |
       |trait Initialize[A]
       |trait Task[A] extends Initialize[Task[A]]
       |
       |extension [A1](inline in: Initialize[A1])
       |  inline def value: A1 = ???
       |
       |extension [A1](inline in: Initialize[Task[A1]])
       |  @targetName("valueIA1")
       |  inline def value: A1 = ???
       |
       |def usage(): Unit = {
       |  val task: Task[String] = ???
       |  ${START}task.value${END}
       |}
       |//String
       |""".stripMargin
  )

  def testExtensionsFromGiven(): Unit = doTest(
    s"""
       |import scala.annotation.targetName
       |
       |trait Initialize[A]
       |trait Task[A] extends Initialize[Task[A]]
       |
       |trait Extensions:
       |  extension [A1](inline in: Initialize[A1])
       |    inline def value: A1 = ???
       |
       |  extension [A1](inline in: Initialize[Task[A1]])
       |    @targetName("valueIA1")
       |    inline def value: A1 = ???
       |
       |given Extensions = ???
       |
       |def usage(): Unit = {
       |  val task: Task[String] = ???
       |  ${START}task.value${END}
       |}
       |//String
       |""".stripMargin
  )

  def testSCL24725(): Unit = doTest(
    s"""
      |import scala.language.implicitConversions
      |
      |trait EntityQuery[T]
      |trait Quoted[T]
      |
      |extension [T](entity: EntityQuery[T]) {
      |  def insertValue(value: T): Unit = println("A")
      |}
      |extension [T](quotedEntity: Quoted[EntityQuery[T]]) {
      |  def insertValue(value: T): Unit = println("B")
      |}
      |implicit def autoQuote[T](body: T): Quoted[T] = ???
      |
      |def query[T]: EntityQuery[T] = new EntityQuery[T] {}
      |
      |@main def main(): Unit = {
      |  ${START}query[String].insertValue("")$END // prints A
      |}
      |//Unit
      |""".stripMargin
  )

  def testSCL23234(): Unit = doTest(
    s"""
      |object Main {
      |  extension (v: Int) {
      |    inline def compress: Int = ???
      |  }
      |  extension (v: Double) {
      |    inline def compress: Double = ???
      |  }
      |
      |  def main(args: Array[String]): Unit = {
      |    ${START}0.compress$END
      |  }
      |}
      |//Int
      |""".stripMargin
  )

  def testDirectConcreteReceiverDeterminesResultTypeInsteadOfConvertedReceiver(): Unit = doTest(
    s"""
       |class DirectReceiver
       |class ConvertedReceiver
       |class DirectResult
       |class ConvertedResult
       |
       |given Conversion[DirectReceiver, ConvertedReceiver] = null
       |
       |extension (receiver: ConvertedReceiver) def choose: ConvertedResult = null
       |extension (receiver: DirectReceiver) def choose: DirectResult = null
       |
       |${START}(null: DirectReceiver).choose${END}
       |//DirectResult
       |""".stripMargin
  )

  def testDirectGenericReceiverDeterminesResultTypeInsteadOfConvertedConcreteReceiver(): Unit = doTest(
    s"""
       |class DirectReceiver
       |class ConvertedReceiver
       |class DirectResult
       |class ConvertedResult
       |
       |given Conversion[DirectReceiver, ConvertedReceiver] = null
       |
       |extension (receiver: ConvertedReceiver) def choose: ConvertedResult = null
       |extension [T](receiver: T) def choose: DirectResult = null
       |
       |${START}(null: DirectReceiver).choose${END}
       |//DirectResult
       |""".stripMargin
  )

  def testDirectSubtypeExtensionReceiverDeterminesResultTypeInsteadOfSupertypeReceiver(): Unit = doTest(
    s"""
       |class ParentReceiver
       |class ChildReceiver extends ParentReceiver
       |class ParentResult
       |class ChildResult
       |
       |extension (receiver: ParentReceiver) def choose: ParentResult = null
       |extension (receiver: ChildReceiver) def choose: ChildResult = null
       |
       |${START}(null: ChildReceiver).choose${END}
       |//ChildResult
       |""".stripMargin
  )

  def testDirectReceiverExtensionFromGivenWinsBeforeConvertedReceiverExtensionFromSameGiven(): Unit = doTest(
    s"""
       |class DirectReceiver
       |class ConvertedReceiver
       |class DirectResult
       |class ConvertedResult
       |
       |trait ExtensionProvider:
       |  extension (receiver: DirectReceiver) def choose: DirectResult = null
       |  extension (receiver: ConvertedReceiver) def choose: ConvertedResult = null
       |
       |given ExtensionProvider = null
       |given Conversion[DirectReceiver, ConvertedReceiver] = null
       |
       |${START}(null: DirectReceiver).choose${END}
       |//DirectResult
       |""".stripMargin
  )

  def testOnlyConvertibleReceiverExtensionIsSelectedAmongMultipleTargets(): Unit = doTest(
    s"""
       |class OriginalReceiver
       |class ConvertibleReceiver
       |class UnreachableReceiver
       |class ConvertibleResult
       |class UnreachableResult
       |
       |given Conversion[OriginalReceiver, ConvertibleReceiver] = null
       |
       |extension (receiver: ConvertibleReceiver) def choose: ConvertibleResult = null
       |extension (receiver: UnreachableReceiver) def choose: UnreachableResult = null
       |
       |${START}(null: OriginalReceiver).choose${END}
       |//ConvertibleResult
       |""".stripMargin
  )
}
