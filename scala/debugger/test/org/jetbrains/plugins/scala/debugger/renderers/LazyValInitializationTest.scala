package org.jetbrains.plugins.scala
package debugger
package renderers

import com.intellij.xdebugger.XDebuggerTestUtil
import com.intellij.xdebugger.frame.XFullValueEvaluator.XFullValueEvaluationCallback
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import com.intellij.xdebugger.frame.{XDebuggerTreeNodeHyperlink, XFullValueEvaluator, XNamedValue, XValue, XValuePlace}
import com.intellij.xdebugger.impl.ui.tree.nodes.{XValueNodeEx, XValuePresentationUtil}
import org.junit.Assert.{assertNotNull, assertNull}
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.util.concurrent.ConcurrentLinkedQueue
import javax.swing.Icon
import scala.compiletime.uninitialized
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Promise, TimeoutException}
import scala.jdk.CollectionConverters.*

class LazyValInitializationTest_2_13 extends LazyValInitializationTestBase(ScalaVersion.Latest.Scala_2_13) {

  addSourceFile("LocalLazyVal.scala",
    s"""object LocalLazyVal {
       |  def main(args: Array[String]): Unit = {
       |    lazy val local: Int = "42".toInt
       |    println(args.length) $breakpoint
       |    println(local)
       |  }
       |}
       |""".stripMargin)

  @Test
  def testInitializeLocalLazyVal(): Unit = {
    createLocalProcess("LocalLazyVal")

    doWhenXSessionPausedThenResume { () =>
      assertInitializedOnClick(frameVariable("local"), expectedType = "lazy Int", expectedValue = "42")

      val reloaded = presentation(frameVariable("local"), "42")
      assertNull(reloaded.fullValueEvaluator)
    }
  }
}

class LazyValInitializationTest_3_3 extends LazyValInitializationScala3TestBase(ScalaVersion.Latest.Scala_3_3)

class LazyValInitializationTest_3_10 extends LazyValInitializationScala3TestBase(ScalaVersion.Latest.Scala_3_10)

class LazyValInitializationTest_3_LTS_RC extends LazyValInitializationScala3TestBase(ScalaVersion.Latest.Scala_3_LTS_RC)

class LazyValInitializationTest_3_Next_RC extends LazyValInitializationScala3TestBase(ScalaVersion.Latest.Scala_3_Next_RC)

abstract class LazyValInitializationScala3TestBase(scalaVersion: ScalaVersion) extends LazyValInitializationTestBase(scalaVersion) {

  addSourceFile("LazyValBeingInitialized.scala",
    s"""object LazyValBeingInitialized {
       |  class Holder {
       |    lazy val first: Int = {
       |      "1".toInt $breakpoint
       |    }
       |
       |    lazy val second: Int = "2".toInt
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    val holder = new Holder
       |    println(holder.first)
       |  }
       |}
       |""".stripMargin)

  @Test
  def testNoInitializeLinkWhileLazyValIsBeingInitialized(): Unit = {
    createLocalProcess("LazyValBeingInitialized")

    doWhenXSessionPausedThenResume { () =>
      val first = presentation(field("this", "first"), DebuggerBundle.message("lazy.val.being.initialized"))
      assertNull(first.fullValueEvaluator)

      // The thread is suspended in `first$lzyINIT1`, so initializing other lazy vals is not offered
      val second = presentation(field("this", "second"), DebuggerBundle.message("lazy.val.not.initialized"))
      assertNull(second.fullValueEvaluator)
    }
  }

  // The accessor of a private lazy val that another class uses gets an expanded name, `…Holder$$touched`, while its
  // field keeps the plain one, `touched$lzy1`, SCL-26219
  addSourceFile("PrivateLazyValReadFromInnerClass.scala",
    s"""object PrivateLazyValReadFromInnerClass {
       |  class Holder {
       |    private lazy val touched: Int = "7".toInt
       |    private lazy val untouched: Int = "42".toInt
       |
       |    class Reader {
       |      def readTouched(): Int = touched
       |      def readUntouched(): Int = untouched
       |    }
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    val holder = new Holder
       |    println(new holder.Reader().readTouched())
       |    println(holder) $breakpoint
       |  }
       |}
       |""".stripMargin)

  @Test
  def childrenOfPrivateLazyValsReadFromAnInnerClass(): Unit = {
    createLocalProcess("PrivateLazyValReadFromInnerClass")

    doWhenXSessionPausedThenResume { () =>
      assertEquals(Seq("touched", "untouched"), childNames("holder").sorted)
      presentation(field("holder", "touched"), "7")
      assertInitializedOnClick(field("holder", "untouched"), expectedType = "int", expectedValue = "42")
    }
  }

  // An object in a class is a lazy val, too: the shape of `ClassfileParser.innerClasses` in the Scala 3 compiler
  addSourceFile("PrivateObjectReadFromInnerClass.scala",
    s"""object PrivateObjectReadFromInnerClass {
       |  class Parser {
       |    private object touched {
       |      override def toString: String = "touched object"
       |    }
       |
       |    private object untouched {
       |      override def toString: String = "untouched object"
       |    }
       |
       |    class Completer {
       |      def complete(): String = touched.toString
       |      def unused(): String = untouched.toString
       |    }
       |
       |    def parse(): String = {
       |      val completed = new Completer().complete()
       |      completed $breakpoint
       |    }
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    println(new Parser().parse())
       |  }
       |}
       |""".stripMargin)

  @Test
  def childrenOfPrivateObjectsReadFromAnInnerClass(): Unit = {
    createLocalProcess("PrivateObjectReadFromInnerClass")

    doWhenXSessionPausedThenResume { () =>
      assertEquals(Seq("touched", "untouched"), childNames("this").sorted)
      presentationMatching(field("this", "touched"), "The touched object")(_.value.contains("touched object"))
      assertInitializedOnClick(field("this", "untouched"), expectedType = "PrivateObjectReadFromInnerClass$Parser$untouched$") {
        _.value.contains("untouched object")
      }
    }
  }
}

/**
 * Tests the "Initialize" link of not initialized lazy vals in the variables view, SCL-26103.
 *
 * The values are presented the way the split debugger does it on the backend. The XValueNode is an `XValueNodeEx`
 * and clicking the link calls [[XFullValueEvaluator.startEvaluation]] with a plain callback, see
 * `com.intellij.xdebugger.impl.rpc.models.XValuePresentationBridge` and
 * `com.intellij.platform.debugger.impl.backend.BackendXValueApi.evaluateFullValue`.
 */
@RunWith(classOf[JUnit4])
abstract class LazyValInitializationTestBase(scalaVersion: ScalaVersion) extends ScalaDebuggerTestCase {

  import LazyValInitializationTestBase.*

  override protected def supportedIn(version: ScalaVersion): Boolean = version == scalaVersion

  addSourceFile("LazyValField.scala",
    s"""object LazyValField {
       |  class Holder {
       |    lazy val field: Int = "42".toInt
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    val holder = new Holder
       |    println(holder) $breakpoint
       |    println(holder.field)
       |  }
       |}
       |""".stripMargin)

  @Test
  def testInitializeLazyValField(): Unit = {
    createLocalProcess("LazyValField")

    doWhenXSessionPausedThenResume { () =>
      assertInitializedOnClick(field("holder", "field"), expectedType = "int", expectedValue = "42")

      val reloaded = presentation(field("holder", "field"), "42")
      assertNull(reloaded.fullValueEvaluator)
    }
  }

  protected def assertInitializedOnClick(value: XValue, expectedType: String, expectedValue: String): Unit =
    assertInitializedOnClick(value, expectedType)(_.value == expectedValue)

  protected def assertInitializedOnClick(value: XValue, expectedType: String)(initialized: Presentation => Boolean): Unit = {
    val node = new RecordingValueNode(value)
    val notInitialized = node.awaitValue(DebuggerBundle.message("lazy.val.not.initialized")) {
      value.computePresentation(node, XValuePlace.TREE)
    }
    assertEquals(expectedType, notInitialized.tpe)

    val initialize = node.fullValueEvaluator
    assertNotNull("The lazy val does not offer an Initialize link", initialize)
    assertEquals(DebuggerBundle.message("initialize.lazy.val"), initialize.getLinkText)

    node.awaitPresentation("The initialized value")(initialized) {
      initialize.startEvaluation(new LinkClickCallback)
    }
    assertNull(node.fullValueEvaluator)
  }

  protected def presentation(value: XValue, expectedValue: String): RecordingValueNode =
    presentationMatching(value, s"The value '$expectedValue'")(_.value == expectedValue)

  protected def presentationMatching(value: XValue, description: String)(matches: Presentation => Boolean): RecordingValueNode = {
    val node = new RecordingValueNode(value)
    node.awaitPresentation(description)(matches) {
      value.computePresentation(node, XValuePlace.TREE)
    }
    node
  }

  protected def frameVariable(name: String): XValue =
    XDebuggerTestUtil.findVar(XDebuggerTestUtil.collectChildren(getDebuggerSession.getXDebugSession.getCurrentStackFrame), name)

  protected def field(variableName: String, fieldName: String): XValue = {
    val variable = frameVariable(variableName)
    XDebuggerTestUtil.computePresentation(variable)
    XDebuggerTestUtil.findVar(XDebuggerTestUtil.collectChildren(variable), fieldName)
  }

  /**
   * Expands the variable like the variables view does and returns the names of its children. Fails if the node shows
   * an error instead, or does not finish in time.
   */
  protected def childNames(variableName: String): Seq[String] = {
    val variable = frameVariable(variableName)
    XDebuggerTestUtil.computePresentation(variable)
    val childrenAndError = XDebuggerTestUtil.collectChildrenWithError(variable)
    assertNull(s"The children of $variableName show an error", childrenAndError.second)
    childrenAndError.first.asScala.toSeq.collect { case named: XNamedValue => named.getName }
  }
}

object LazyValInitializationTestBase {

  final case class Presentation(tpe: String, value: String) {
    override def toString: String = s"{$tpe} $value"
  }

  /**
   * Records the presentations of a value, like the backend node in `XValuePresentationBridge`.
   */
  //noinspection ApiStatus,JetBrainsInternalApiUsage
  final class RecordingValueNode(value: XValue) extends XValueNodeEx {
    private val presentations = new ConcurrentLinkedQueue[Presentation]()

    @volatile private var awaited: Presentation => Unit = _ => ()

    @volatile private var _fullValueEvaluator: XFullValueEvaluator = uninitialized

    def fullValueEvaluator: XFullValueEvaluator = _fullValueEvaluator

    /**
     * Runs `action` and waits until the node presents the `expected` value.
     */
    def awaitValue(expected: String)(action: => Unit): Presentation =
      awaitPresentation(s"The value '$expected'")(_.value == expected)(action)

    /**
     * Runs `action` and waits until the node presents a value that `matches`.
     */
    def awaitPresentation(description: String)(matches: Presentation => Boolean)(action: => Unit): Presentation = {
      val presented = Promise[Presentation]()
      awaited = presentation => if (matches(presentation)) presented.trySuccess(presentation)
      action

      try Await.result(presented.future, XDebuggerTestUtil.TIMEOUT_MS.millis)
      catch {
        case _: TimeoutException =>
          throw new AssertionError(s"$description was not presented, presentations: ${presentations.asScala.mkString("[", ", ", "]")}")
      }
    }

    private def present(presentation: Presentation): Unit = {
      presentations.add(presentation)
      awaited(presentation)
    }

    override def setPresentation(icon: Icon, tpe: String, value: String, hasChildren: Boolean): Unit =
      present(Presentation(tpe, value))

    override def setPresentation(icon: Icon, presentation: XValuePresentation, hasChildren: Boolean): Unit =
      present(Presentation(presentation.getType, XValuePresentationUtil.computeValueText(presentation)))

    override def setFullValueEvaluator(fullValueEvaluator: XFullValueEvaluator): Unit =
      _fullValueEvaluator = fullValueEvaluator

    override def clearFullValueEvaluator(): Unit =
      _fullValueEvaluator = null

    override def getXValue: XValue = value

    override def setInlayIcon(icon: Icon): Unit = {}

    override def addAdditionalHyperlink(link: XDebuggerTreeNodeHyperlink): Unit = {}

    override def clearAdditionalHyperlinks(): Unit = {}

    override def isObsolete: Boolean = false
  }

  /**
   * Like the callback in `BackendXValueApi.evaluateFullValue`, this is not a `HeadlessValueEvaluationCallback`.
   */
  private final class LinkClickCallback extends XFullValueEvaluationCallback {
    override def errorOccurred(errorMessage: String): Unit = {}

    override def isObsolete: Boolean = false
  }
}
