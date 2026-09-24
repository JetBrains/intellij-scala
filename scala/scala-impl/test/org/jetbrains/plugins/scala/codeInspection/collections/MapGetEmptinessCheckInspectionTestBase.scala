package org.jetbrains.plugins.scala
package codeInspection
package collections


abstract class MapGetEmptinessCheckInspectionTestBase extends OperationsOnCollectionInspectionTest {
  override protected val classOfInspection: Class[? <: OperationOnCollectionInspection] =
    classOf[MapGetEmptinessCheckInspection]
}

class MapGetNonEmptyTest extends MapGetEmptinessCheckInspectionTestBase {
  override protected val hint = ScalaInspectionBundle.message("replace.get.nonEmpty.with.contains")

  def testMap(): Unit = doTest(
    s"Map(1 -> 2).${START}get(3).nonEmpty$END",
    "Map(1 -> 2).get(3).isDefined",
    "Map(1 -> 2).contains(3)",
  )
}

class MapGetIsEmptyTest extends MapGetEmptinessCheckInspectionTestBase {
  override protected val hint = ScalaInspectionBundle.message("replace.get.isEmpty.with.not.contains")

  def testMap(): Unit = doTest(
    s"Map(1 -> 2).${START}get(3).isEmpty$END",
    "Map(1 -> 2).get(3).isEmpty",
    "!Map(1 -> 2).contains(3)",
  )
}