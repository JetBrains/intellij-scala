package org.jetbrains.plugins.scala
package lang.adjustTypes.tests

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.lang.adjustTypes.AdjustTypesTestBase

/**
  * Nikolay.Tropin
  * 7/11/13
  */
class AdjustTypesTests extends AdjustTypesTestBase {

  def testSimpleJava(): Unit = doTest()

  def testParameterizedJava(): Unit = doTest()

  def testJavaWithImportAlias(): Unit = doTest()

  def testNameConflicts(): Unit = doTest()

  def testJavaInnerClasses(): Unit = doTest()

  def testPackagings(): Unit = doTest()

  def testAmbiguity(): Unit = doTest()

  def testSeveralLevelImports(): Unit = doTest()

  def testTypeAlias(): Unit = doTest()

  def testTypeProjection(): Unit = doTest()

  def testThisType(): Unit = doTest()

  def testPrefixed(): Unit = doTest()

  def testImportedInnerClass(): Unit = doTest()

  def testInheritedTypeInObject(): Unit = doTest()

  def testRecursiveTypeAlias(): Unit = doTest()
}

class AdjustCatsTypeTest extends AdjustTypesTestBase {

  override protected def additionalLibraries: Seq[LibraryLoader] =
    IvyManagedLoader("org.typelevel" % "cats-core_2.11" % "0.4.0") :: Nil

  def testSCL10006(): Unit = doTest()
}

class AdjustTypeScalaReflectTest extends AdjustTypesTestBase {
  override def isIncludeReflectLibrary: Boolean = true

  def testClassTag(): Unit = doTest()
}