package org.jetbrains.sbt

import org.scalatest.FunSuite
import java.io.File

/**
 * @author Pavel Fatin
 */
class ImportTest extends FunSuite {
  val DataDir = new File("src/test/data")
  val Exclusions = Seq("sbt-idea", "SbtIdea")

  test("bare projects") {
    doTest("bare", download = false)
  }

  test("simple project") {
    doTest("simple")
  }

  test("managed dependency") {
    doTest("dependency")
  }

  test("multiple projects") {
    doTest("multiple")
  }

  private def doTest(project: String, download: Boolean = true) {
    val base = new File(DataDir, project)

    val actual = Loader.load(base, download).filterNot(s => Exclusions.exists(s.contains)).mkString("\n")

    val expected = {
      val text = read(new File(base, "structure.xml")).mkString("\n")
      text.replace("$BASE", FS.toPath(base))
    }

    if (actual != expected) {
      println("Actual output:\n" + actual)
      fail()
    }
  }
}

