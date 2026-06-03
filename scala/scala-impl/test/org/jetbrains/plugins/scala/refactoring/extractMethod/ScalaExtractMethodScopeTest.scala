package org.jetbrains.plugins.scala.refactoring.extractMethod

import com.intellij.idea.TestFor
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class ScalaExtractMethodScopeTest extends ScalaExtractMethodTestBase {
  override def folderPath: Path = super.folderPath / "scope"

  def testNewClassScope(): Unit = doTest()
  def testNewClassScope2(): Unit = doTest()
  def testNewClassScope3(): Unit = doTest()
  def testNewClassScope4(): Unit = doTest()
  def testNewClassScope5(): Unit = doTest()
  def testNewClassScope6(): Unit = doTest()
  def testNewClassScope7(): Unit = doTest()

  @TestFor(issues = Array("SCL-25158"))
  def testDefaultClassScope(): Unit = doTest()

  @TestFor(issues = Array("SCL-25158"))
  def testDefaultClassScope2(): Unit = doTest()

  @TestFor(issues = Array("SCL-25158"))
  def testDefaultClassScope3(): Unit = doTest()
}

class Scala3ExtractMethodScopeTest extends ScalaExtractMethodScopeTest {
  override def supportedIn(version: ScalaVersion): Boolean = version.isScala3
}
