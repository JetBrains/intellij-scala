package org.jetbrains.plugins.scala.testingSupport.test.utest

import com.intellij.psi.PsiClass
import com.intellij.testIntegration.TestFramework
import com.intellij.testIntegration.createTest.CreateTestDialog
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.project.{ProjectPsiElementExt, Version}
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestFramework.TestFrameworkSetupInfo
import org.jetbrains.plugins.scala.testingSupport.test.utest.UTestTestFramework._
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestFramework, TestFrameworkSetupSupportBase}

final class UTestTestFramework extends AbstractTestFramework with TestFrameworkSetupSupportBase {

  override def getName: String = "uTest"

  override def testFileTemplateName(dialog: CreateTestDialog): String = {
    val uTestVersion = UTestVersionUtils.getUTestLibraryVersionForTestModuleOf(dialog.getTargetClass)
    if (uTestVersion.exists(_ < Version("0.9.0")))
      "uTest Object"
    else
      "uTest Class"
  }

  override def getMarkerClassFQName: String = BaseTestSuiteFqn

  override def getDefaultSuperClass: String = BaseTestSuiteFqn

  override def baseSuitePaths: Seq[String] = Seq(BaseTestSuiteFqn)

  override def frameworkSetupInfo(scalaVersion: Option[String]): TestFrameworkSetupInfo =
    TestFrameworkSetupInfo(Seq(""""com.lihaoyi" %% "utest" % "latest.integration" % Test"""), Seq())

  override protected def isTestClass(definition: ScTemplateDefinition): Boolean = {
    // Q: shouldn't something like this be called in the base class for all test frameworks?
    // A lightweight check whether the class could be a test class without inheritors check or resolve.
    UTestTestFramework.isValidUTestSuiteCandidate(definition) &&
      super.isTestClass(definition)
  }
}

object UTestTestFramework {
  val BaseTestSuiteFqn: String = "utest.TestSuite"

  /**
   * Return `true` if the class can be potentially a valid uTest test class without inheritors check.
   * The check is meant to be lightweight. it does not check if the class inherits any base test suite class.
   *
   * @note uTest 0.9.0+ supports both classes and objects<br>
   *       uTest 0.8.x and earlier only support objects
   */
  private[utest] def isValidUTestSuiteCandidate(clazz: PsiClass): Boolean = clazz match {
    case o: ScObject =>
      o.isTopLevel
    case c: ScClass if isUTestVersionSince09(c) =>
      // In some parts this method is similar to `utest.framework.PortableScalaReflectExcerpts.isInstantiatableClass` but not fully
      c.isTopLevel &&
        !c.hasModifierPropertyScala("abstract") &&
        c.constructors.exists(_.parameters.isEmpty) //has default constructor
    case _ =>
      false
  }

  private def isUTestVersionSince09(clazz: PsiClass): Boolean = {
    val libraryVersion = clazz.module.flatMap(UTestVersionUtils.getUTestLibraryVersion)
    // If we can't detect the version for some reason, we act optimistically and assume it to be the latest version
    libraryVersion.forall(_ >= UTestVersionUtils.Version090)
  }

  @deprecated("use `apply` instead", "2020.3")
  def instance: UTestTestFramework = apply()

  def apply(): UTestTestFramework =
    TestFramework.EXTENSION_NAME.findExtension(classOf[UTestTestFramework])
}