package org.jetbrains.plugins.scala.failed.resolve

/**
  * Created by Anton Yalyshev on 15/04/16.
  */
class CollectionsResolveTest extends FailedResolveCaretTestBase {

  def testSCL7209(): Unit = {
    doResolveCaretTest(
      """
        |class mc {
        |  for {
        |    x <- List(1,2,3)
        |    y: List[String] = ???
        |    z = y.<caret>map(_.head) // red because (y:Nothing) is inferred
        |  } yield ()
        |}
      """.stripMargin)
  }

  def testSCL5756(): Unit = doResolveCaretTest(
    s"""
       |class Person {
       |    def toMarkdown: String = "text"
       |  }
       |
       |  object Person {
       |    implicit def t(b: Source): Set[Person] = Set.empty
       |  }
       |
       |  object TEst {
       |    def main(args: Array[String]) {
       |      for (filename <- args;
       |           people: Set[Person] = Source.fromFile(filename);
       |           person <- people;
       |           markdown = person.<caret>toMarkdown)
       |        yield markdown
       |    }
       |  }
    """.stripMargin)

  def testSCL11222(): Unit = doResolveCaretTest(
    s"""
       |  def get(id: Long)(implicit x: Int = 10): Option[Int] = ???
       |
       |  def getAll(ids: Set[Long]): Set[Int] = {
       |    ids.<caret>flatMap(get)
       |  }
    """.stripMargin)

  def testSCL13665(): Unit = doResolveCaretTest(
    s"""
       |val foo = <caret>Array[Byte](1, 2, 3)
    """.stripMargin)
}
