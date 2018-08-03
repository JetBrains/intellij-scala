package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * Created by Anton Yalyshev on 15/04/16.
  */
@Category(Array(classOf[PerfCycleTests]))
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

  def testSCL13747(): Unit = doResolveCaretTest(
    s"""
       |import java.util.Random
       |  class State[S,A](run: S => (A, S)) {
       |    def map[B](f: A=>B): State[S,B] = new State ( s => {
       |      val (a,s2) = run(s)
       |      (f(a), s2)
       |    })
       |  }
       |  class RNGXX(rnd: Random) extends State[RNGXX, Int] (
       |    rng => (rng.nextInt, new RNGXX(rnd))
       |  ) {
       |    val nextInt: Int = rnd.nextInt()
       |  }
       |  val r1 = new RNGXX(new Random(5))
       |
       |  r1.<caret>map(x => x + 1)
    """.stripMargin)
}
