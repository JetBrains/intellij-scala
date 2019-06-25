package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi.PsiReference
import org.junit.Assert._

/**
  * Created by katejim on 5/26/16.
  */
class ResolvePackagesWithBacktickeds extends ScalaResolveTestCase {
  override def folderPath: String = s"${super.folderPath}resolve/packages/backtickeds"

  override protected def sourceRootPath: String = folderPath

  private def checkReference() = {
    val ref: PsiReference = findReferenceAtCaret
    assertTrue(ref.resolve != null)
  }

  def testInFileBacktickedPackage() = checkReference()

  def testFromJavaPackage() = checkReference()

  def testClassInPackageWithJavaKeyword() = checkReference()

  def testJavaClass() =
    checkReference()

  def testScalaClass() = checkReference()

  def testMethodInBactickedsPackage() = checkReference()

}
