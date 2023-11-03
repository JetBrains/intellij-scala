package org.jetbrains.plugins.scala
package debugger
package positionManager

import com.intellij.debugger.{DebuggerInvocationUtil, SourcePosition}
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.{PsiDocumentManager, PsiManager}
import com.sun.jdi.Location
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.junit.Assert
import org.junit.Assert.fail

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import scala.collection.mutable
import scala.jdk.CollectionConverters._

abstract class PositionManagerTestBase extends ScalaDebuggerTestCase {

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

  protected def checkGetAllClasses(mainClass: String = getTestName(false))
                                  (expectedClassNames: String*): Unit = {
    checkGetAllClassesInFile(mainClass)(s"${mainClass.split('.').mkString(File.separator)}.scala")(expectedClassNames: _*)
  }

  protected def checkGetAllClassesInFile(mainClass: String = getTestName(false))
                                        (filePath: String)
                                        (expectedClassNames: String*): Unit = {
    createOffsetsForFile(filePath)
    createLocalProcess(mainClass)

    val sourcePositions = sourcePositionsForFile(filePath)

    val debugProcess = getDebugProcess
    val positionManager = ScalaPositionManager.instance(debugProcess).getOrElse(new ScalaPositionManager(debugProcess))

    doWhenPausedThenResume { _ =>
      stoppedAtBreakpoint.set(true)

      for ((position, className) <- sourcePositions.zip(expectedClassNames)) {
        val classes = positionManager.getAllClasses(position)
        val classNames = classes.asScala.map(_.name())
        Assert.assertTrue(s"Wrong classes are found at $position (found: ${classNames.mkString(", ")}, expected: $className)",
          classNames.contains(className))
      }
    }
  }

  private def sourcePositionsForFile(filePath: String): List[SourcePosition] = inReadAction {
    val manager = PsiManager.getInstance(getProject)
    val path = srcPath.resolve(filePath)
    val virtualFile = VfsUtil.findFile(path, true)
    val psiFile = inReadAction(manager.findFile(virtualFile))
    sourcePositionOffsets.result().map { offset =>
      val fromOffset = SourcePosition.createFromOffset(psiFile, offset)
      SourcePosition.createFromLine(psiFile, fromOffset.getLine)
    }
  }

  private def createOffsetsForFile(filePath: String): Unit = {
    val manager = PsiManager.getInstance(getProject)
    val path = srcPath.resolve(filePath)
    val virtualFile = VfsUtil.findFile(path, true)
    val psiFile = inReadAction(manager.findFile(virtualFile))

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
  }

  protected def checkLocationsOfLine(mainClass: String = getTestName(false))
                                    (expectedLocations: Set[Loc]*): Unit = {
    val filePath = s"${mainClass.split('.').mkString(File.separator)}.scala"
    createOffsetsForFile(filePath)
    createLocalProcess(mainClass)

    val sourcePositions = sourcePositionsForFile(filePath)
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
