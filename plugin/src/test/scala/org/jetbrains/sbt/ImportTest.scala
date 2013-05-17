package org.jetbrains.sbt

import org.scalatest.FunSuite
import java.io.File
import scala.io.Source

/**
 * @author Pavel Fatin
 */
class ImportTest extends FunSuite {
  val DataDir = new File("src/test/data")

  test("simple project") {
    doTest("simple")
  }

  test("managed dependency") {
    doTest("dependency")
  }

  test("multiple projects") {
    doTest("multiple")
  }

  private def doTest(project: String) {
    val base = new File(DataDir, project)

    val actual = Loader.load(base)

    val expected = {
      val text = Source.fromFile(new File(base, "structure.xml")).getLines().mkString("\n")
      text.replace("$BASE", FS.toPath(base))
    }

    if (actual != expected) {
      println("Actual output:\n" + actual)
      fail()
    }
  }
}

