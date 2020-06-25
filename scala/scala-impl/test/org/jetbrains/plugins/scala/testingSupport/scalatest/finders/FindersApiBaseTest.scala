package org.jetbrains.plugins.scala.testingSupport.scalatest.finders

import com.intellij.testFramework.EdtTestUtil
import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestAstTransformer
import org.junit.Assert.{assertEquals, assertNotNull}
import org.scalatest.finders.Selection

trait FindersApiBaseTest extends ScalaTestTestCase {

  def checkSelection(lineNumber: Int, offset: Int, fileName: String, testNames: Set[String]): Unit = {
    val location = createLocation(lineNumber, offset, fileName)
    val selection = EdtTestUtil.runInEdtAndGet[Option[Selection]] { () =>
      ScalaTestAstTransformer.testSelection(location)
    }.orNull
    assertNotNull(s"selection is null for $fileName:$lineNumber:$offset", selection)
    assertEquals(testNames, selection.testNames().map(_.trim).toSet)
  }
}
