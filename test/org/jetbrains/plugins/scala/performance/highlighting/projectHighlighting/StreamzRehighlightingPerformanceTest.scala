package org.jetbrains.plugins.scala.performance.highlighting.projectHighlighting

import com.intellij.openapi.editor.LogicalPosition
import org.jetbrains.plugins.scala.SlowTests
import org.junit.Ignore
import org.junit.experimental.categories.Category

import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Try}

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[SlowTests]))
class StreamzRehighlightingPerformanceTest extends RehighlightingPerformanceTypingTestBase {

  override protected def getExternalSystemConfigFileName: String = "build.sbt"

  def githubUsername: String = "krasserm"

  //a small project using scalaz-stream and akka
  def githubRepoName: String = "streamz"

  def revision: String = "559aa356291e1760e72f59816df70bdcf169d089"

  @Ignore
  def testTypingCamelPackageObj(): Unit = {
    val fileName = "streamz-akka-camel/src/main/scala/streamz/akka/camel/package.scala"
    val tries = ArrayBuffer[Try[Unit]]()

    tries += Try(doTest(fileName, 1.seconds, Seq("val i = Some(10)\n"), new LogicalPosition(43, 1), Some("def foo(): Unit = {\n"), Some("Fun with return type")))
    tries += Try(doTest(fileName, 10.seconds, Seq("val i = Some(10)\n"), new LogicalPosition(43, 1), Some("def foo() = {\n"), Some("Fun without return type")))
    tries += Try(doTest(fileName, 10.seconds, Seq("val i = Some(10)\n"), new LogicalPosition(43, 1), None, Some("Class body")))

    val thrown = tries.collect {
      case Failure(t) => t
    }
    thrown.foreach(_.printStackTrace(System.err))
    thrown.headOption match {
      case Some(t) => throw t
      case _ =>
    }
  }
}
