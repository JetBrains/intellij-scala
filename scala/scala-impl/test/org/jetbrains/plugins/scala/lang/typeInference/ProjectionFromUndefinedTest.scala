package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class ProjectionFromUndefinedTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL17581(): Unit = checkTextHasNoErrors(
    """
      |object IncorrectHighlitingDemo extends App {
      |  abstract class AbstractTable {
      |    type TableElementType
      |  }
      |  abstract class Table[T] extends AbstractTable {
      |    final override type TableElementType = T
      |  }
      |  abstract class Query[E, U] {
      |    def doSomething(): Unit = println("doSomething")
      |  }
      |  class TableQuery[E <: AbstractTable] extends Query[E, E#TableElementType] {}
      |  implicit class TableQueryExtensions[T <: AbstractTable](query: Query[T, T#TableElementType]) {
      |    def doSomethingElse(): Unit = println("doSomethingElse")
      |  }
      |  class MyTable extends Table[Int]
      |  object MyTableQuery extends TableQuery[MyTable]
      |  new TableQueryExtensions(MyTableQuery)
      |}
      |""".stripMargin
  )
}
