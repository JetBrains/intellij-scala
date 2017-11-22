package org.jetbrains.plugins.scala.debugger.positionManager

import com.intellij.debugger.SourcePosition
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.{PsiFile, PsiManager}
import com.sun.jdi.Location
import org.jetbrains.plugins.scala.debugger.{Loc, ScalaDebuggerTestCase, ScalaPositionManager}
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.junit.Assert

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * @author Nikolay.Tropin
 */
abstract class PositionManagerTestBase extends ScalaDebuggerTestCase {
  protected val offsetMarker = "<offset>"
  protected val sourcePositionsOffsets = mutable.HashMap[String, Seq[Int]]()

  protected def checkGetAllClassesInFile(fileName: String)(expectedClassNames: String*): Unit = {
    val sourcePositions = sourcePositionsInFile(fileName)

    runDebugger() {
      waitForBreakpoint()
      for ((position, className) <- sourcePositions.zip(expectedClassNames)) {
        val classes = managed {
          positionManager.getAllClasses(position)
        }
        val classNames = classes.asScala.map(_.name())
        Assert.assertTrue(
          s"Wrong classes are found at ${position.toString} (found: ${classNames.mkString(", ")}, expected: $className",
          classNames.contains(className)
        )
      }
    }
  }

  protected def checkGetAllClasses(expectedClassNames: String*): Unit = checkGetAllClassesInFile(mainFileName)(expectedClassNames: _*)

  protected def checkLocationsOfLine(expectedLocations: Set[Loc]*): Unit = {
    val sourcePositions = sourcePositionsInFile(mainFileName)
    Assert.assertEquals("Wrong number of expected locations sets: ", sourcePositions.size, expectedLocations.size)

    runDebugger() {
      waitForBreakpoint()

      def checkSourcePosition(initialPosition: SourcePosition, location: Location) = {
        inReadAction {
          val newPosition = positionManager.getSourcePosition(location)
          Assert.assertEquals(initialPosition.getFile, newPosition.getFile)
          Assert.assertEquals(initialPosition.getLine, newPosition.getLine)
        }
      }

      for ((position, locationSet) <- sourcePositions.zip(expectedLocations)) {
        val foundLocations: Set[Loc] = managed {
          val classes = positionManager.getAllClasses(position)
          val locations = classes.asScala.flatMap(refType => positionManager.locationsOfLine(refType, position).asScala)
          locations.foreach(checkSourcePosition(position, _))
          locations.map(toSimpleLocation).toSet
        }
        Assert.assertTrue(s"Wrong locations are found at ${position.toString} (found: $foundLocations, expected: $locationSet", locationSet.subsetOf(foundLocations))
      }
    }
  }

  private def toSimpleLocation(location: Location) = Loc(location.declaringType().name(), location.method().name(), location.lineNumber())

  protected def setupFile(fileName: String, fileText: String, hasOffsets: Boolean = true): Unit = {
    val breakpointLine = fileText.lines.indexWhere(_.contains(bp))
    var cleanedText = fileText.replace(bp, "").replace("\r", "")
    val offsets = ArrayBuffer[Int]()
    var offset = cleanedText.indexOf(offsetMarker)
    while (offset >= 0) {
      offsets += offset
      cleanedText = cleanedText.substring(0, offset) + cleanedText.substring(offset + offsetMarker.length)
      offset = cleanedText.indexOf(offsetMarker)
    }

    assert(!hasOffsets || offsets.nonEmpty, s"Not specified offset marker in test case. Use $offsetMarker in provided text of the file.")
    sourcePositionsOffsets += (fileName -> offsets)
    addSourceFile(fileName, cleanedText)

    if (breakpointLine >= 0)
      addBreakpoint(breakpointLine, fileName)
  }

  private def createLineSourcePositionFromOffset(file: PsiFile, offset: Int) = {
    val fromOffset = SourcePosition.createFromOffset(file, offset)
    SourcePosition.createFromLine(file, fromOffset.getLine)
  }

  private def sourcePositionsInFile(fileName: String) = inReadAction {
    val psiManager = PsiManager.getInstance(getProject)
    val vFile = VfsUtil.findFileByIoFile(getFileInSrc(fileName), false)
    val psiFile = psiManager.findFile(vFile)
    val offsets = sourcePositionsOffsets(fileName)
    offsets.map(createLineSourcePositionFromOffset(psiFile, _))
  }
}