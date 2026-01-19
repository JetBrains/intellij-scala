package org.jetbrains.plugins.scala.refactoring.rename3

import com.intellij.refactoring.JavaRefactoringSettings
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.util.RevertableChange

final class ScalaAutomaticRenamerTest extends ScalaRenameTestBase {
  override def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  def testAutomaticRenamer(): Unit = doTest(newName = "Bar", withAutoRenames = true)

  def testAutomaticRenamerJavaClass(): Unit = doTest(newName = "Bar", withAutoRenames = true)

  def testAutomaticRenamerJavaParameter(): Unit = doTest(newName = "aa", withAutoRenames = true)

  def testAutomaticRenamerOverloads(): Unit = doTest(newName = "bar", withAutoRenames = true)

  def testAutomaticRenamerOverloadsClass(): Unit = doTest(newName = "bar", withAutoRenames = true)

  def testAutomaticRenamerOverloadsExtensionAndMember(): Unit = doTest(newName = "funMe2", withAutoRenames = true)

  def testAutomaticRenamerOverloadsJavaClass(): Unit = doTest(newName = "bar", withAutoRenames = true)

  def testAutomaticRenamerParameter(): Unit = doTest(newName = "aa", withAutoRenames = true)

  def testAutomaticRenamerParameterExtensionTarget(): Unit = doTest(newName = "receiver", withAutoRenames = true)

  // TODO: do not disable Java renamer
  //       for now it suggests a wrong parameter for extension methods and is automatically applied in tests
  def testAutomaticRenamerParameterInExtension(): Unit =
    RevertableChange.withModifiedSetting(JavaRefactoringSettings.getInstance())(false)(
      _.RENAME_PARAMETER_IN_HIERARCHY,
      _.RENAME_PARAMETER_IN_HIERARCHY = _,
    ).run {
      doTest(newName = "aa", withAutoRenames = true)
    }

  def testAutomaticRenamerParameterMultipleClauses(): Unit = doTest(newName = "cc", withAutoRenames = true)
}
