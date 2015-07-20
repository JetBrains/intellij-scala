package org.jetbrains.plugins.scala.debugger.positionManager

import com.intellij.debugger.SourcePosition
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import com.sun.jdi.Location
import org.jetbrains.plugins.scala.debugger.{ScalaDebuggerTestCase, ScalaPositionManager}
import org.jetbrains.plugins.scala.extensions
import org.junit.Assert

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

/**
 * @author Nikolay.Tropin
 */
abstract class PositionManagerTestBase extends ScalaDebuggerTestCase {
  protected val offsetMarker = "<offset>"
  protected val bp = "<breakpoint>"
  protected val fileName = "Test.scala"

  //fileText should contain object Main with method main
  protected def checkGetAllClasses(fileText: String, expectedClassNames: String*) = {
    val sourcePositions = setupFileAndCreateSourcePositions(fileText.stripMargin.trim.replace("\r",""))
    Assert.assertEquals("Wrong number of expected class names: ", sourcePositions.size, expectedClassNames.size)

    runDebugger("Main") {
      waitForBreakpoint()
      val posManager = new ScalaPositionManager(getDebugProcess)
      for ((position, className) <- sourcePositions.zip(expectedClassNames)) {
        val classes = managed {
          posManager.getAllClasses(position)
        }
        val classNames = classes.asScala.map(_.name())
        Assert.assertTrue(s"Wrong classes are found at ${position.toString} (found: ${classNames.mkString(", ")}, expected: $className", classNames.contains(className))
      }
    }
  }

  protected def checkLocationsOfLine(fileText: String, expectedLocations: Set[Loc]*): Unit = {
    val sourcePositions = setupFileAndCreateSourcePositions(fileText.stripMargin.trim.replace("\r",""))
    Assert.assertEquals("Wrong number of expected locations sets: ", sourcePositions.size, expectedLocations.size)

    runDebugger("Main") {
      waitForBreakpoint()
      val posManager = new ScalaPositionManager(getDebugProcess)

      def checkSourcePosition(initialPosition: SourcePosition, location: Location) = {
        extensions.inReadAction {
          val newPosition = posManager.getSourcePosition(location)
          Assert.assertEquals(initialPosition.getFile, newPosition.getFile)
          Assert.assertEquals(initialPosition.getLine, newPosition.getLine)
        }
      }

      for ((position, locationSet) <- sourcePositions.zip(expectedLocations)) {
        val foundLocations: Set[Loc] = managed {
          val classes = posManager.getAllClasses(position)
          val locations = classes.asScala.flatMap(refType => posManager.locationsOfLine(refType, position).asScala)
          locations.foreach(checkSourcePosition(position, _))
          locations.map(toSimpleLocation).toSet
        }
        Assert.assertTrue(s"Wrong locations are found at ${position.toString} (found: $foundLocations, expected: $locationSet", locationSet.subsetOf(foundLocations))
      }
    }
  }

  private def toSimpleLocation(location: Location) = Loc(location.declaringType().name(), location.method().name(), location.lineNumber())

  protected def setupFileAndCreateSourcePositions(fileText: String): Seq[SourcePosition] = {
    val breakpointLine = fileText.lines.indexWhere(_.contains(bp))
    var cleanedText = fileText.replace(bp, "")
    val offsets = ArrayBuffer[Int]()
    var offset = cleanedText.indexOf(offsetMarker)
    while (offset >= 0) {
      offsets += offset
      cleanedText = cleanedText.substring(0, offset) + cleanedText.substring(offset + offsetMarker.length)
      offset = cleanedText.indexOf(offsetMarker)
    }

    assert(offsets.nonEmpty, s"Not specified offset marker in test case. Use $offsetMarker in provided text of the file.")
    addFileToProject(fileName, cleanedText)

    if (breakpointLine >= 0)
      addBreakpoint(fileName, breakpointLine)

    val psiManager = PsiManager.getInstance(getProject)
    val vFile = VfsUtil.findFileByIoFile(getFileInSrc(fileName), false)
    val psiFile = psiManager.findFile(vFile)

    offsets.map(SourcePosition.createFromOffset(psiFile,_))
  }
}

case class Loc(className: String, methodName: String, line: Int)