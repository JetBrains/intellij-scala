package org.jetbrains.plugins.scala.failed.resolve

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.resolve.ResolvableReferenceElement
import org.junit.experimental.categories.Category

/**
  * Created by Anton Yalyshev
  */
@Category(Array(classOf[PerfCycleTests]))
class CollectionsResolveTest extends SimpleTestCase {

  def doResolveTest (code: String) {
    val (psi, caretPos) = parseText(code, EditorTestUtil.CARET_TAG)
    val reference = psi.findElementAt(caretPos).getParent
    reference match {
      case r: ResolvableReferenceElement => assert(r.resolve() != null, "failed to resolve enclosing object")
      case _ => assert(true)
    }
  }

  def testSCL7209(): Unit = {
    doResolveTest(
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

  def testSCL5756(): Unit = doResolveTest(
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
}
