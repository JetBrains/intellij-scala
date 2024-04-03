package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class RelatedGenericsTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12

  def testSCL9347(): Unit = checkTextHasNoErrors(
    """
      |object SCL9347 {
      |  trait Record
      |  trait MutableRecord[TR <: Record] {
      |    def table: Table[TR, MutableRecord[TR]]
      |  }
      |
      |  // Important: MTR must be related to TR
      |  class Table[TR <: Record, MTR <: MutableRecord[TR]] {selfTable =>
      |    class Field {
      |      def table = selfTable
      |    }
      |  }
      |
      |  type AnyTable = Table[TR, _ <: MutableRecord[TR]] forSome {type TR <: Record}
      |
      |  def needTable(table: AnyTable) = ???
      |
      |  def foo(field: AnyTable#Field) = {
      |    field.table // Good code red
      |  }
      |
      |  def foo2(field: Table[TR, MTR]#Field forSome {type TR <: Record; type MTR <: MutableRecord[TR]}) = {
      |    field.table // Good code red
      |  }
      |
      |  def bar(mutableRecord: MutableRecord[_ <: Record]) {
      |    needTable(mutableRecord.table) // Good code red
      |  }
      |}
    """.stripMargin)

  def testSCL11156(): Unit = checkTextHasNoErrors(
    """
      |trait Parser[T] extends Function[String, Option[(T, String)]]
      |
      |val item: Parser[Char] = {
      |  case v => Some((v.charAt(0), v.substring(1)))
      |  case "" => None
      |}
    """.stripMargin)
}
