package org.jetbrains.plugins.scala.lang.resolve2

import org.jetbrains.plugins.scala.ScalaVersion

/** Also see [[org.jetbrains.plugins.scala.lang.resolve.ScaladocLinkResolveTest_Old]] */
abstract class ScalaDocLinkResolveTestBase extends ResolveTestBaseWithAlternativeExpectedData {

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

class ScalaDocLinkResolveTest extends ScalaDocLinkResolveTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  override def folderPath: String =
    s"${super.folderPath}scaladoc"
}

class ScalaDocLinkResolveTest_Scala3 extends ResolveTestBaseWithAlternativeExpectedData {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version.isScala3

  override def folderPath: String =
    s"${super.folderPath}scaladoc3"

  def testParametersAndTypeParametersInEnumCases(): Unit = doTest()
}
