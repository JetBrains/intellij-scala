package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi.PsiReference
import org.jetbrains.plugins.scala.extensions.PathExt
import org.junit.Assert._

import java.nio.file.Path

class ResolvePackagesWithBacktickeds extends ScalaResolveTestCase {
  override def folderPath: Path = super.folderPath / "resolve" / "packages" / "backtickeds"

  override protected def sourceRootPath: Path = folderPath

  private def checkReference(): Unit = {
    val ref: PsiReference = findReferenceAtCaret()
    assertTrue(ref.resolve != null)
  }

  def testInFileBacktickedPackage(): Unit = checkReference()

  def testFromJavaPackage(): Unit = checkReference()

  def testClassInPackageWithJavaKeyword(): Unit = checkReference()

  def testJavaClass(): Unit =
    checkReference()

  def testScalaClass(): Unit = checkReference()

  def testMethodInBactickedsPackage(): Unit = checkReference()

}
