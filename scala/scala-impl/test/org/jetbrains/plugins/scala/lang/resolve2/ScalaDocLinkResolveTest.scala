package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.ScalaVersion

/** Also see [[org.jetbrains.plugins.scala.lang.resolve.ScaladocLinkResolveTest_Old]] */
class ScalaDocLinkResolveTest extends ResolveTestBaseWithAlternativeExpectedData {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  override def folderPath: String =
    s"${super.folderPath}scaladoc"

  override def treatMultipleResolveResultsAsUnresolvedReference: Boolean = false

  override def setUp(): Unit = {
    super.setUp()
    val newFile = myFixture.addFileToProject("test.scala", getFile.getText)
    myFixture.openFileInEditor(newFile.getVirtualFile)
  }

  def testLinksToClassesAndObjects(): Unit = doTest()

  def testLinksToClassesAndObjects_FullyQualified(): Unit = doTest()

  def testLinksToMembersOfClassesAndObjects(): Unit = doTest()

  def testLinksToMembersOfClassesAndObjects_FullyQualified(): Unit = doTest()

  def testLinksToMembersOfScalaDocOwnerTypeDefinition(): Unit = doTest()
}
