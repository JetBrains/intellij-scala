package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class TypeTest extends ResolveTestBase {
  override def folderPath: Path = super.folderPath / "type"

  def testClassParameter(): Unit = doTest()
  def testClassTypeParameter(): Unit = doTest()
  def testDependentMethodTypeBound(): Unit = doTest()
  def testFunction(): Unit = doTest()
  def testFunctionParameter(): Unit = doTest()
  def testFunctionTypeParameter(): Unit = doTest()
  def testValue(): Unit = doTest()
  def testVariable(): Unit = doTest()
  def testThis(): Unit = doTest()
  def testTypeProjection(): Unit = doTest()
}
