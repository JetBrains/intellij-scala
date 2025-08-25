package org.jetbrains.plugins.scala.debugger.breakpoints

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.testFramework.UsefulTestCase.assertInstanceOf
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.{XDebuggerBundle, XDebuggerManager}
import junit.framework.TestCase.{assertEquals, assertFalse, assertTrue}
import org.jetbrains.java.debugger.breakpoints.properties.JavaExceptionBreakpointProperties
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.{DebuggerTests, ScalaVersion}
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters._

@Category(Array(classOf[DebuggerTests]))
class ExceptionBreakpointsTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  def testNew(): Unit = {
    checkAvailable(methodBody(s"val o = new Runtime${CARET}Exception()"), "java.lang.RuntimeException")
  }

  // TODO Also test the scala.RuntimeException type alias, SCL-24247
  def testType(): Unit = {
    checkAvailable(methodBody(s"val o: java.lang.${CARET}Throwable = new RuntimeException()"), "java.lang.Throwable")
  }

  def testTypeUnavailable(): Unit = {
    checkUnavailable(methodBody(s"val o: ${CARET}java.lang.Object = new RuntimeException()"))
  }

  // TODO Also test the scala.RuntimeException type alias, SCL-24247
  def testCatch(): Unit = {
    checkAvailable(
      methodBody(s"try {} catch { case e: java.lang.Runtime${CARET}Exception => }"),
      "java.lang.RuntimeException"
    )
  }

  def testThrow(): Unit = {
    checkAvailable(
      methodBody(s"try {} catch { case e: RuntimeException => ${CARET}throw e }"),
      "java.lang.RuntimeException"
    )
  }

  def testClass(): Unit = {
    checkAvailable(s"class ${CARET}A extends RuntimeException", "A")
  }

  def testClassUnavailable(): Unit = {
    checkUnavailable(s"class ${CARET}A")
  }

  // TODO Also test the scala.NumberFormatException type alias, SCL-24247
  def testParseInt(): Unit = {
    checkAvailable(
      s"""def parseInt(s: String): Int =
         |  try s.toInt
         |  catch
         |    case e: java.lang.NumberFor${CARET}matException =>
         |      e.printStackTrace()
         |      -1
         |""".stripMargin,
      "java.lang.NumberFormatException"
    )
  }

  def testCreateDisableEnable(): Unit = {
    val b = configureAndCreateBreakpoint(methodBody(s"throw new Runtime${CARET}Exception"), "java.lang.RuntimeException")
    assertTrue(b.isEnabled)
    invokeBreakpointAction()
    assertFalse(b.isEnabled)
    invokeBreakpointAction()
    assertTrue(b.isEnabled)
  }

  override protected def setUp(): Unit = {
    super.setUp()
    removeAllBreakpoints()
  }

  override protected def tearDown(): Unit =
    try removeAllBreakpoints()
    finally super.tearDown()

  private def removeAllBreakpoints(): Unit = {
    val manager = XDebuggerManager.getInstance(getProject).getBreakpointManager
    manager.getAllBreakpoints.foreach(manager.removeBreakpoint)
  }

  private def configure(text: String): Unit = {
    assertTrue(text.contains(CARET))
    configureFromFileText(text)
    getFixture.doHighlighting()
  }

  private def getActions(): Seq[IntentionAction] = {
    val actions = CodeInsightTestFixtureImpl.getAvailableIntentions(getEditor, getFile).asScala.toSeq
    actions.filter(_.getFamilyName == XDebuggerBundle.message("xdebugger.intention.control.exception.breakpoint.family"))
  }

  private def invokeBreakpointAction(): Unit = invokeAndWait {
    getActions().head.invoke(getProject, getEditor, getFile)
  }

  private def configureAndCreateBreakpoint(text: String, exceptionName: String): XBreakpoint[JavaExceptionBreakpointProperties] = {
    configure(text)

    // Default breakpoints are not removed during setUp, so we have to ignore them here.
    val breakpointsBefore = XDebuggerManager.getInstance(getProject).getBreakpointManager.getAllBreakpoints
    invokeBreakpointAction()
    val breakpointsAfter = XDebuggerManager.getInstance(getProject).getBreakpointManager.getAllBreakpoints
    val theBreakpoint = (breakpointsAfter.toSet -- breakpointsBefore.toSet).head
    assertTrue(theBreakpoint.isEnabled)
    val properties = assertInstanceOf(theBreakpoint.getProperties, classOf[JavaExceptionBreakpointProperties])
    assertEquals(exceptionName, properties.myQualifiedName)
    theBreakpoint.asInstanceOf[XBreakpoint[JavaExceptionBreakpointProperties]]
  }

  private def checkAvailable(text: String, exceptionName: String): Unit = {
    configureAndCreateBreakpoint(text, exceptionName)
  }

  private def checkUnavailable(text: String): Unit = {
    configure(text)
    assertTrue(getActions().isEmpty)
  }

  private def methodBody(body: String): String =
    s"class A { def f(): Unit = { $body } }"
}
