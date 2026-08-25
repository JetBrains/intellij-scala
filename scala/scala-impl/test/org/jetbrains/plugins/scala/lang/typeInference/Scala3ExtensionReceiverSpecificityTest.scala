package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.ScalaVersion

class Scala3ExtensionReceiverSpecificityTest extends TypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

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

  def testGenericExtensionReceiverSpecificityKeepsReceiverTypeInference(): Unit = doTest(
    s"""
       |trait Initialize[A]
       |trait Task[A] extends Initialize[Task[A]]
       |
       |extension [A](value: Initialize[A]) def result: A = null.asInstanceOf[A]
       |extension [A](value: Initialize[Task[A]]) def result: A = null.asInstanceOf[A]
       |
       |val task: Task[Int] = null
       |${START}task.result${END}
       |//Int
       |""".stripMargin
  )

  def testGenericExtensionsFromGivenKeepReceiverTypeInferenceDuringSpecificitySelection(): Unit = doTest(
    s"""
       |trait Initialize[A]
       |trait Task[A] extends Initialize[Task[A]]
       |
       |trait ExtensionProvider:
       |  extension [A](value: Initialize[A]) def result: A = null.asInstanceOf[A]
       |  extension [A](value: Initialize[Task[A]]) def result: A = null.asInstanceOf[A]
       |
       |given ExtensionProvider = null
       |
       |val task: Task[Int] = null
       |${START}task.result${END}
       |//Int
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
