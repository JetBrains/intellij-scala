package org.jetbrains.plugins.scala
package debugger
package positionManager

import com.intellij.debugger.{DebuggerInvocationUtil, SourcePosition}
import com.intellij.openapi.application.ModalityState
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import com.sun.jdi.Location
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.junit.Assert
import org.junit.Assert.fail

import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import scala.collection.mutable
import scala.jdk.CollectionConverters._

abstract class NewPositionManagerTestBase extends NewScalaDebuggerTestCase {

  protected case class Loc(cls: String, method: String, line: Int)

  protected val offsetMarker: String = "/* offset */"

  private val sourcePositionOffsets: mutable.ListBuffer[Int] = mutable.ListBuffer.empty

  private val stoppedAtBreakpoint: AtomicBoolean = new AtomicBoolean(false)

  override protected def tearDown(): Unit = {
    try {
      if (!stoppedAtBreakpoint.getAndSet(false)) {
        fail("The debugger did not stop at a breakpoint")
      }
    } finally {
      super.tearDown()
    }
  }

  override protected def createBreakpoints(className: String): Unit = {
    val manager = ScalaPsiManager.instance(getProject)
    val psiClass = inReadAction(manager.getCachedClass(GlobalSearchScope.allScope(getProject), className))
    val psiFile = psiClass.map(_.getContainingFile).getOrElse(throw new AssertionError(s"Could not find class $className"))

    val runnable: Runnable = () => {
      val document = PsiDocumentManager.getInstance(getProject).getDocument(psiFile)
      val text = document.getText

      val offsets = mutable.ArrayBuffer.empty[Int]
      var offset = text.indexOf(offsetMarker)
      while (offset != -1) {
        offsets += offset
        offset = text.indexOf(offsetMarker, offset + 1)
      }

      sourcePositionOffsets ++= offsets
    }

    if (!SwingUtilities.isEventDispatchThread) {
      DebuggerInvocationUtil.invokeAndWait(getProject, runnable, ModalityState.defaultModalityState())
    } else {
      runnable.run()
    }

    super.createBreakpoints(className)
  }

  protected def checkLocationsOfLine(mainClass: String = getTestName(false))
                                    (expectedLocations: Set[Loc]*): Unit = {
    createLocalProcess(mainClass)

    val sourcePositions = inReadAction {
      val manager = ScalaPsiManager.instance(getProject)
      val psiClass = inReadAction(manager.getCachedClass(GlobalSearchScope.allScope(getProject), mainClass))
      val psiFile = psiClass.map(_.getContainingFile).getOrElse(throw new AssertionError(s"Could not find class $mainClass"))
      sourcePositionOffsets.result().map { offset =>
        val fromOffset = SourcePosition.createFromOffset(psiFile, offset)
        SourcePosition.createFromLine(psiFile, fromOffset.getLine)
      }
    }

    Assert.assertEquals("Wrong number of expected locations sets: ", expectedLocations.size, sourcePositions.size)

    val debugProcess = getDebugProcess
    val positionManager = ScalaPositionManager.instance(debugProcess).getOrElse(new ScalaPositionManager(debugProcess))

    def checkSourcePosition(initialPosition: SourcePosition, location: Location): Unit = inReadAction {
      val newPosition = positionManager.getSourcePosition(location)
      assertEquals(initialPosition.getFile, newPosition.getFile)
      assertEquals(initialPosition.getLine, newPosition.getLine)
    }

    doWhenPausedThenResume { _ =>
      stoppedAtBreakpoint.set(true)

      for ((position, locationSet) <- sourcePositions.zip(expectedLocations)) {
        val foundLocations = {
          val classes = positionManager.getAllClasses(position)
          val locations = classes.asScala.flatMap(refType => positionManager.locationsOfLine(refType, position).asScala)
          locations.foreach(checkSourcePosition(position, _))
          locations.map { l =>
            Loc(l.declaringType().name(), l.method().name(), l.lineNumber())
          }.toSet
        }
        Assert.assertTrue(s"Wrong locations are found at $position (found: $foundLocations, expected: $locationSet)", locationSet.subsetOf(foundLocations))
      }
    }
  }
}
