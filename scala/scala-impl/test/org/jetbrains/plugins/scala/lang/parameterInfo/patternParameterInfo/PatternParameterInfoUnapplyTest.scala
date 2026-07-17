package org.jetbrains.plugins.scala.lang.parameterInfo.patternParameterInfo

import org.jetbrains.plugins.scala.base.libraryLoaders.{LibraryLoader, ScalaStandardLibraryLoaders}

class PatternParameterInfoUnapplyTest extends PatternParameterInfoTestBase {

  // `scala.xml` (used by `testUnapplySeq`) is a separate module since Scala 2.11
  override protected def additionalLibraries: Seq[LibraryLoader] = ScalaStandardLibraryLoaders.scalaXmlLoaders

  override def getTestDataPath: String =
    s"${super.getTestDataPath}unapply/"

  def testCompoundTypeField(): Unit = doTest()

  def testCompoundTypeParam(): Unit = doTest()

  def testUnapply(): Unit = doTest()

  def testUnapplySeq(): Unit = doTest()

  def testWithLocalTypeInference(): Unit = doTest()

  def testSelfType(): Unit = doTest()
}
