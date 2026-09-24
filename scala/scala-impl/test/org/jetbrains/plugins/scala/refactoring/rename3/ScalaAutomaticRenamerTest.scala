package org.jetbrains.plugins.scala.refactoring.rename3

import com.intellij.refactoring.JavaRefactoringSettings
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.util.RevertableChange
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
final class ScalaAutomaticRenamerTest extends ScalaRenameTestBase {
  override def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  @Test
  def testAutomaticRenamer(): Unit = doTest(newName = "Bar", withAutoRenames = true)

  @Test
  def testAutomaticRenamerJavaClass(): Unit = doTest(newName = "Bar", withAutoRenames = true)

  @Test
  def testAutomaticRenamerJavaParameter(): Unit = doTest(newName = "aa", withAutoRenames = true)

  @Test
  def testAutomaticRenamerOverloads(): Unit = doTest(newName = "bar", withAutoRenames = true)

  @Test
  def testAutomaticRenamerOverloadsClass(): Unit = doTest(newName = "bar", withAutoRenames = true)

  @Test
  def testAutomaticRenamerOverloadsExtensionAndMember(): Unit = doTest(newName = "funMe2", withAutoRenames = true)

  @Test
  def testAutomaticRenamerOverloadsJavaClass(): Unit = doTest(newName = "bar", withAutoRenames = true)

  @Test
  def testAutomaticRenamerParameter(): Unit = doTest(newName = "aa", withAutoRenames = true)

  @Test
  def testAutomaticRenamerParameterExtensionTarget(): Unit = doTest(newName = "receiver", withAutoRenames = true)

  // TODO: do not disable Java renamer
  //       for now it suggests a wrong parameter for extension methods and is automatically applied in tests
  @Test
  def testAutomaticRenamerParameterInExtension(): Unit =
    RevertableChange.withModifiedSetting(JavaRefactoringSettings.getInstance())(false)(
      _.RENAME_PARAMETER_IN_HIERARCHY,
      _.RENAME_PARAMETER_IN_HIERARCHY = _,
    ).run {
      doTest(newName = "aa", withAutoRenames = true)
    }

  @Test
  def testAutomaticRenamerParameterMultipleClauses(): Unit = doTest(newName = "cc", withAutoRenames = true)
}
