package org.jetbrains.plugins.scala.runner

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerInfo.LineMarkerGutterIconRenderer
import com.intellij.execution.RunManager
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.impl.{RunConfigurationLevel, RunManagerImpl, RunnerAndConfigurationSettingsImpl}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.configurations.TestLocation.{CaretLocation2, PsiElementLocation}
import org.jetbrains.plugins.scala.configurations.{RunConfigurationCreationOps, TestLocation}
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers._
import org.junit.Assert.{assertNotNull, assertNull, fail}
import org.junit.ComparisonFailure

import scala.jdk.CollectionConverters.ListHasAsScala

abstract class ScalaApplicationConfigurationProducerTestBase
  extends ScalaFixtureTestCaseWithSourceFolder
    with RunConfigurationCreationOps {

  private def configurationProducer = ScalaApplicationConfigurationProducer()

  protected def createConfiguration(location: TestLocation): ApplicationConfiguration = {
    val context = configurationContext(location)
    val configuration = configurationProducer.createConfigurationFromContext(context)
    assertNotNull(s"no configuration created at location: $location", configuration)
    configuration.getConfiguration.asInstanceOf[ApplicationConfiguration]
  }

  protected def findOrCreateConfiguration(location: TestLocation): ApplicationConfiguration = {
    val context = configurationContext(location)
    val configuration = configurationProducer.findOrCreateConfigurationFromContext(context)
    configuration.getConfiguration.asInstanceOf[ApplicationConfiguration]
  }

  protected def configurationContext(location: TestLocation): ConfigurationContext = {
    val psiElement = location match {
      case loc: CaretLocation2         => findPsiElement(loc, getProject)
      case PsiElementLocation(element) => element
      case _                           => ???
    }
    new ConfigurationContext(psiElement)
  }

  protected def configurationContext(psiElement: PsiElement): ConfigurationContext =
    new ConfigurationContext(psiElement)

  protected def doTest(location: TestLocation, configName: String, mainClassName: String): ApplicationConfiguration = {
    val configuration = createConfiguration(location)
    assertConfiguration(configuration, configName, mainClassName)
    configuration
  }

  protected def assertConfiguration(configuration: ApplicationConfiguration, configName: String, mainClassName: String): Unit = {
    configuration.getName shouldBe  configName
    configuration.getMainClassName shouldBe mainClassName

    /**
     * Check that the class is valid and there is no red error in the Run Configuration UI, in the class field.
     *
     * Under the hood this call indirectly tests these entities:
     *  - [[com.intellij.psi.util.PsiMethodUtil.findMainMethod]]
     *  - [[org.jetbrains.plugins.scala.runner.ScalaMainMethodProvider]]
     */
    configuration.checkClass()
  }

  protected def assertNoConfiguration(testLocation: TestLocation): Unit = {
    val context = configurationContext(testLocation)
    val configuration = configurationProducer.createConfigurationFromContext(context)
    assertNull(s"no configuration is expected to be created at location: $testLocation", configuration)
  }

  protected def assertIsTheSame(expected: ApplicationConfiguration, actual: ApplicationConfiguration): Unit ={
    if (!(expected eq actual)) {
      throw new ComparisonFailure(null, toStringDebug(expected), toStringDebug(actual))
    }
  }

  private def toStringDebug(conf: ApplicationConfiguration): String =
    conf.toString + s", hashcode: ${conf.hashCode()}, main class name: ${conf.getMainClassName}"

  protected val PackageStatementPlaceholder = "<package>"

  protected def addFileToProjectSources(filePackage: Option[String], fileName: String, fileText: String): VirtualFile = {
    val filePackagePath = filePackage.fold("")(_.replace('.', '/') + "/")
    val packageStatement = filePackage.fold("")(p => s"package $p")

    val fileRelativePath = filePackagePath + fileName
    val fileTextWithFixedPackage = fileText.replace(PackageStatementPlaceholder, packageStatement)
    addFileToProjectSources(fileRelativePath, fileTextWithFixedPackage)
  }

  protected def saveConfigurationsInRunManager(configurations: ApplicationConfiguration*): Unit = {
    val manager = RunManager.getInstance(getProject).asInstanceOf[RunManagerImpl]
    configurations.foreach { config =>
      val configSettings = new RunnerAndConfigurationSettingsImpl(manager, config, false, RunConfigurationLevel.PROJECT)
      manager.addConfiguration(configSettings)
    }
  }
}

class ScalaApplicationConfigurationProducerTest_Scala2 extends ScalaApplicationConfigurationProducerTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version < ScalaVersion.Latest.Scala_3_0

  // Emulate creating of configuration from the project view
  def testCreateFromFile_ShouldSelectFirstMainInFile(): Unit = {
    val pack = "org.example"
    val packagePrefix = pack + "."

    val vFile = addFileToProjectSources(Some(pack), "FileWithMain.scala",
      s"""$PackageStatementPlaceholder
         |
         |object MyMainObject1 {
         |  def main(args: Array[String]): Unit = {}
         |}
         |
         |object MyMainObject2 {
         |  def main(args: Array[String]): Unit = {}
         |}
         |""".stripMargin
    )

    val psiFile = findPsiFile(vFile, getProject)
    val location = PsiElementLocation(psiFile)
    val config1 = doTest(location, "MyMainObject1", packagePrefix + "MyMainObject1")

    saveConfigurationsInRunManager(config1)

    val config11 = findOrCreateConfiguration(location)
    assertIsTheSame(config1, config11)
  }

  def testCreateFromTopLevelWhitespace_ShouldSelectFirstMainInFile(): Unit = {
    val pack = "org.example"
    val packagePrefix = pack + "."

    val vFile = addFileToProjectSources(Some(pack), "FileWithMain.scala",
      s"""$PackageStatementPlaceholder
         |//location
         |
         |object MyMainObject1 {
         |  def main(args: Array[String]): Unit = {}
         |}
         |
         |object MyMainObject2 {
         |  def main(args: Array[String]): Unit = {}
         |}
         |""".stripMargin
    )

    val location = CaretLocation2(vFile, 1, 0)
    val config1 = doTest(location, "MyMainObject1", packagePrefix + "MyMainObject1")

    saveConfigurationsInRunManager(config1)

    val config11 = findOrCreateConfiguration(location)

    assertIsTheSame(config1, config11)
  }

  def testMainInObject_InNestedPackaging(): Unit = {
    val pack = "org.example"
    val packagePrefix = pack + "."

    val vFile = addFileToProjectSources(Some(pack), "FileWithMain.scala",
      s"""$PackageStatementPlaceholder
         |
         |package a {
         |  object OA {  def main(args: Array[String]): Unit = { println("OA") } } // locationA1
         |  //locationA2
         |
         |  package b {
         |    object OB {  def main(args: Array[String]): Unit = {println("OB")} } // locationB1
         |    //locationB2
         |
         |  }
         |}
         |""".stripMargin
    )

    val locationA1 = CaretLocation2(vFile, 3, 10)
    val locationA2 = CaretLocation2(vFile, 4, 0)
    val locationA3 = CaretLocation2(vFile, 5, 0)

    val locationB1 = CaretLocation2(vFile, 7, 10)
    val locationB2 = CaretLocation2(vFile, 8, 0)
    val locationB3 = CaretLocation2(vFile, 9, 0)

    val configA = doTest(locationA1, "OA", packagePrefix + "a.OA")
    val configB = doTest(locationB1, "OB", packagePrefix + "a.b.OB")

    saveConfigurationsInRunManager(
      configA,
      configB,
    )

    val configA1Reused = findOrCreateConfiguration(locationA1)
    val configA2Reused = findOrCreateConfiguration(locationA2)
    val configA3Reused = findOrCreateConfiguration(locationA3)

    val configB1Reused = findOrCreateConfiguration(locationB1)
    val configB2Reused = findOrCreateConfiguration(locationB2)
    val configB3Reused = findOrCreateConfiguration(locationB3)

    assertIsTheSame(configA, configA1Reused)
    assertIsTheSame(configA, configA2Reused)
    assertIsTheSame(configA, configA3Reused)

    assertIsTheSame(configB, configB1Reused)
    assertIsTheSame(configB, configB2Reused)
    assertIsTheSame(configB, configB3Reused)
  }

  // Scala doesn't support main methods in nested objects even if they have stable path
  def testMainInNestedObjectShouldNotBeDetectedAsMainMethod(): Unit ={
    val pack = "org.example"

    val text =
      s"""$PackageStatementPlaceholder
         |
         |object ObjectOuter {
         |  object ObjectInner {
         |    def main(args: Array[String]): Unit = {
         |
         |    }
         |  }
         |}
         |""".stripMargin.trim
    val vFile = addFileToProjectSources(Some(pack), "A.scala", text)

    val linesCount = text.linesIterator.size
    (0 until linesCount).foreach { lineIdx =>
      assertNoConfiguration(CaretLocation2(vFile, lineIdx, 0))
    }

    myFixture.openFileInEditor(vFile)
    val gutters: Seq[LineMarkerInfo[_]] = myFixture.findAllGutters.asScala.toSeq.map(_.asInstanceOf[LineMarkerGutterIconRenderer[_]].getLineMarkerInfo)
    val runGutters = gutters.filter(_.getIcon == ScalaRunLineMarkerContributor.RunIcon)

    if (runGutters.nonEmpty) {
      val document = myFixture.getDocument(myFixture.getFile)
      val lines = runGutters.map(_.startOffset).map(document.getLineNumber)
      fail(s"No run gutters expected but found at lines: ${lines.mkString(", ")}")
    }
  }

  //SCL-11082
  def testMainFromScalaAppWithCompanionClass(): Unit = {

    val pack = "org.example"
    val packagePrefix = pack + "."

    val vFile = addFileToProjectSources(Some(pack), "FileWithMain.scala",
      s"""$PackageStatementPlaceholder
         |
         |object Foo extends App {
         |  println("Hello World!")
         |}
         |
         |class Foo
         |""".stripMargin
    )

    val location1 = CaretLocation2(vFile, 1, 0)
    val location2 = CaretLocation2(vFile, 2, 10)
    val location3 = CaretLocation2(vFile, 3, 5)
    val location4 = CaretLocation2(vFile, 6, 1)

    val config1 = doTest(location1, "Foo", packagePrefix + "Foo")
    doTest(location2, "Foo", packagePrefix + "Foo")
    doTest(location3, "Foo", packagePrefix + "Foo")
    doTest(location4, "Foo", packagePrefix + "Foo")

    saveConfigurationsInRunManager(
      config1, // save only 1, cause other are equal
    )

    val configReused1 = findOrCreateConfiguration(location1)
    val configReused2 = findOrCreateConfiguration(location2)
    val configReused3 = findOrCreateConfiguration(location3)
    val configReused4 = findOrCreateConfiguration(location4)

    assertIsTheSame(config1, configReused1)
    assertIsTheSame(config1, configReused2)
    assertIsTheSame(config1, configReused3)
    assertIsTheSame(config1, configReused4)
  }

  //SCL-11082
  def testMainWithCompanionClass(): Unit = {

    val pack = "org.example"
    val packagePrefix = pack + "."

    val vFile = addFileToProjectSources(Some(pack), "FileWithMain.scala",
      s"""$PackageStatementPlaceholder
         |
         |object Foo {
         |  def main(args: Array[String]): Unit = {}
         |}
         |
         |class Foo
         |""".stripMargin
    )

    val location1 = CaretLocation2(vFile, 1, 0)
    val location2 = CaretLocation2(vFile, 2, 10)
    val location3 = CaretLocation2(vFile, 3, 5)
    val location4 = CaretLocation2(vFile, 6, 1)

    val config1 = doTest(location1, "Foo", packagePrefix + "Foo")
    doTest(location2, "Foo", packagePrefix + "Foo")
    doTest(location3, "Foo", packagePrefix + "Foo")
    doTest(location4, "Foo", packagePrefix + "Foo")

    saveConfigurationsInRunManager(
      config1, // save only 1, cause other are equal
    )

    val configReused1 = findOrCreateConfiguration(location1)
    val configReused2 = findOrCreateConfiguration(location2)
    val configReused3 = findOrCreateConfiguration(location3)
    val configReused4 = findOrCreateConfiguration(location4)

    assertIsTheSame(config1, configReused1)
    assertIsTheSame(config1, configReused2)
    assertIsTheSame(config1, configReused3)
    assertIsTheSame(config1, configReused4)
  }
}

class ScalaApplicationConfigurationProducerTest_Scala3 extends ScalaApplicationConfigurationProducerTest_Scala2 {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_0

  def testAnnotatedMainFunction(): Unit = {
    commonTestAnnotatedMainFunction(Some("org.example"))
  }

  def testAnnotatedMainFunction_RootPackage(): Unit = {
    commonTestAnnotatedMainFunction(None)
  }

  protected def commonTestAnnotatedMainFunction(pack: Option[String]): Unit = {
    val packagePrefix = pack.fold("")(_ + ".")

    val vFile = addFileToProjectSources(pack, "topLevelFunctions.scala",
      s"""$PackageStatementPlaceholder
         |
         |@main
         |def mainFoo(args: String*): Unit = {
         |}
         |
         |@main
         |def mainFooWithCustomParams(param1: Int, param2: String, other: String): Unit = {
         |}
         |
         |@main
         |def mainFooWithCustomParamsWithVararg(param1: Int, param2: String, other: String): Unit = {
         |}
         |
         |@main
         |def mainFooWithoutParams(): Unit = {
         |}
         |""".stripMargin
    )

    doTest(CaretLocation2(vFile, 3, 10), "mainFoo", packagePrefix + "mainFoo")
    doTest(CaretLocation2(vFile, 7, 10), "mainFooWithCustomParams", packagePrefix + "mainFooWithCustomParams")
    doTest(CaretLocation2(vFile, 11, 10), "mainFooWithCustomParamsWithVararg", packagePrefix + "mainFooWithCustomParamsWithVararg")
    doTest(CaretLocation2(vFile, 15, 10), "mainFooWithoutParams", packagePrefix + "mainFooWithoutParams")
  }

  def testAnnotatedMainFunction_InObject(): Unit = {
    val pack = "org.example"
    val packagePrefix = pack + "."

    val vFile = addFileToProjectSources(Some(pack), "MyObject1.scala",
      s"""$PackageStatementPlaceholder
         |
         |object MyObject1 {
         |  @main
         |  def mainFooInObject1(args: String*): Unit = {
         |  }
         |
         |  object MyObject2 {
         |    @main
         |    def mainFooInObject2(args: String*): Unit = {
         |    }
         |  }
         |}
         |""".stripMargin
    )

    doTest(CaretLocation2(vFile, 4, 10), "mainFooInObject1", packagePrefix + "mainFooInObject1")
    doTest(CaretLocation2(vFile, 9, 10), "mainFooInObject2", packagePrefix + "mainFooInObject2")
  }

  def testAnnotatedMainFunction_InSameFileWithOrdinaryMainFunction(): Unit = {
    val pack = "org.example3"
    val packagePrefix = pack + "."

    val vFile = addFileToProjectSources(Some(pack), "topLevelDefinitions.scala",
      s"""$PackageStatementPlaceholder
         |
         |@main
         |def mainFoo1(args: String*): Unit = { // location1
         |}
         |
         |@main
         |def mainFoo2(args: String*): Unit = { // location2
         |}
         |
         |object MyObject1 { // location3
         |  def main(args: Array[String]): Unit = {
         |  }
         |}
         |
         |object MyObject2 { // location4
         |
         |  def main(args: Array[String]): Unit = { // location5
         |  }
         |
         |  @main
         |  def mainFoo3InObject(args: String*): Unit = { // location6
         |  }
         |}
         |""".stripMargin
    )

    val location1 = CaretLocation2(vFile, 3, 10)
    val location2 = CaretLocation2(vFile, 7, 10)
    val location3 = CaretLocation2(vFile, 10, 10)
    val location4 = CaretLocation2(vFile, 15, 10)
    val location5 = CaretLocation2(vFile, 17, 10)
    val location6 = CaretLocation2(vFile, 21, 10)

    val config1 = doTest(location1, "mainFoo1", packagePrefix + "mainFoo1")
    val config2 = doTest(location2, "mainFoo2", packagePrefix + "mainFoo2")
    val config3 = doTest(location3, "MyObject1", packagePrefix + "MyObject1")
    val config4 = doTest(location4, "MyObject2", packagePrefix + "MyObject2")
    val config5 = doTest(location6, "mainFoo3InObject", packagePrefix + "mainFoo3InObject")

    saveConfigurationsInRunManager(
      config1,
      config2,
      config3,
      config4,
      config5
    )

    val config11 = findOrCreateConfiguration(location1)
    val config22 = findOrCreateConfiguration(location2)
    val config33 = findOrCreateConfiguration(location3)
    val config44 = findOrCreateConfiguration(location4)
    val config55 = findOrCreateConfiguration(location5)
    val config66 = findOrCreateConfiguration(location6)

    assertIsTheSame(config1, config11)
    assertIsTheSame(config2, config22)
    assertIsTheSame(config3, config33)
    assertIsTheSame(config4, config44)
    assertIsTheSame(config4, config55) // config created when right clicking on main method should be the same when clicking on object itself
    assertIsTheSame(config5, config66)
  }

  def testAnnotatedMainFunction_InSameFileNearSingleTopLevelClass(): Unit = {
    val pack = "org.example3"
    val packagePrefix = pack + "."

    val vFile = addFileToProjectSources(Some(pack), "topLevelDefinitions.scala",
      s"""$PackageStatementPlaceholder
         |
         |class A {
         |}
         |
         |@main
         |def mainFoo1(args: String*): Unit = { // location1
         |}
         |
         |@main
         |def mainFoo2(args: String*): Unit = { // location2
         |}
         |
         |""".stripMargin
    )

    val location1 = CaretLocation2(vFile, 6, 10)
    val location2 = CaretLocation2(vFile, 10, 10)

    val config1 = doTest(location1, "mainFoo1", packagePrefix + "mainFoo1")
    val config2 = doTest(location2, "mainFoo2", packagePrefix + "mainFoo2")

    saveConfigurationsInRunManager(
      config1,
      config2,
    )

    val config11 = findOrCreateConfiguration(location1)
    val config22 = findOrCreateConfiguration(location2)

    assertIsTheSame(config1, config11)
    assertIsTheSame(config2, config22)
  }

  def testAnnotatedMainFunction_InSameFileNearSeveralTopLevelClasses(): Unit = {
    val pack = "org.example3"
    val packagePrefix = pack + "."

    val vFile = addFileToProjectSources(Some(pack), "topLevelDefinitions.scala",
      s"""$PackageStatementPlaceholder
         |
         |class A {
         |}
         |
         |@main
         |def mainFoo1(args: String*): Unit = { // location1
         |}
         |
         |class B
         |
         |@main
         |def mainFoo2(args: String*): Unit = { // location2
         |}
         |""".stripMargin
    )

    val location1 = CaretLocation2(vFile, 6, 10)
    val location2 = CaretLocation2(vFile, 12, 10)

    val config1 = doTest(location1, "mainFoo1", packagePrefix + "mainFoo1")
    val config2 = doTest(location2, "mainFoo2", packagePrefix + "mainFoo2")

    saveConfigurationsInRunManager(
      config1,
      config2,
    )

    val config11 = findOrCreateConfiguration(location1)
    val config22 = findOrCreateConfiguration(location2)

    assertIsTheSame(config1, config11)
    assertIsTheSame(config2, config22)
  }

  def testAnnotatedMainFunctionNearObjectMainFunction(): Unit = {
    val pack = "org.example"
    val packagePrefix = pack + "."

    val vFile = addFileToProjectSources(Some(pack), "topLevelDefinitions.scala",
      s"""$PackageStatementPlaceholder
         |
         |object O3 {
         |  def main(args: Array[String]): Unit = println(1)
         |
         |  @main def mainInObject(): Unit = println(2)
         |}
         |""".stripMargin
    )

    val location1 = CaretLocation2(vFile, 3, 20)
    val location2 = CaretLocation2(vFile, 5, 20)

    val config1 = doTest(location1, "O3", packagePrefix + "O3")
    val config2 = doTest(location2, "mainInObject", packagePrefix + "mainInObject")

    saveConfigurationsInRunManager(
      config1,
      config2,
    )

    val config11 = findOrCreateConfiguration(location1)
    val config22 = findOrCreateConfiguration(location2)

    assertIsTheSame(config1, config11)
    assertIsTheSame(config2, config22)
  }

  def testCreateFromFile_ShouldSelectFirstMainInFile_FirstIsAnnotatedMainMethod(): Unit = {
    val pack = "org.example"
    val packagePrefix = pack + "."

    val vFile = addFileToProjectSources(Some(pack), "FileWithMain.scala",
      s"""$PackageStatementPlaceholder
         |
         |@main def mainTopLevel1(): Unit = println(42)
         |
         |object MyMainObject1 {
         |  def main(args: Array[String]): Unit = {}
         |}
         |
         |@main def mainTopLevel2(): Unit = println(42)
         |
         |object MyMainObject2 {
         |  def main(args: Array[String]): Unit = {}
         |}
         |""".stripMargin
    )

    val psiFile = findPsiFile(vFile, getProject)
    val location = PsiElementLocation(psiFile)
    val config1 = doTest(location, "mainTopLevel1", packagePrefix + "mainTopLevel1")

    saveConfigurationsInRunManager(config1)

    val config11 = findOrCreateConfiguration(location)
    assertIsTheSame(config1, config11)
  }

  def testCreateFromFile_ShouldSelectFirstMainInFile_FirstIsObjectMainMethod(): Unit = {
    val pack = "org.example"
    val packagePrefix = pack + "."

    val vFile = addFileToProjectSources(Some(pack), "FileWithMain.scala",
      s"""$PackageStatementPlaceholder
         |
         |object MyMainObject1 {
         |  def main(args: Array[String]): Unit = {}
         |}
         |
         |@main def mainTopLevel1(): Unit = println(42)
         |
         |object MyMainObject2 {
         |  def main(args: Array[String]): Unit = {}
         |}
         |
         |@main def mainTopLevel2(): Unit = println(42)
         |""".stripMargin
    )

    val psiFile = findPsiFile(vFile, getProject)
    val location = PsiElementLocation(psiFile)
    val config1 = doTest(location, "MyMainObject1", packagePrefix + "MyMainObject1")

    saveConfigurationsInRunManager(config1)

    val config11 = findOrCreateConfiguration(location)
    assertIsTheSame(config1, config11)
  }

  def testCreateFromTopLevelWhitespace_ShouldSelectFirstMainInFile_FirstIsAnnotatedMainMethod(): Unit = {
    val pack = "org.example"
    val packagePrefix = pack + "."

    val vFile = addFileToProjectSources(Some(pack), "FileWithMain.scala",
      s"""$PackageStatementPlaceholder
         |//location
         |
         |@main def mainTopLevel1(): Unit = println(42)
         |
         |object MyMainObject1 {
         |  def main(args: Array[String]): Unit = {}
         |}
         |
         |@main def mainTopLevel2(): Unit = println(42)
         |
         |object MyMainObject2 {
         |  def main(args: Array[String]): Unit = {}
         |}
         |""".stripMargin
    )

    val location = CaretLocation2(vFile, 1, 0)
    val config1 = doTest(location, "mainTopLevel1", packagePrefix + "mainTopLevel1")

    saveConfigurationsInRunManager(config1)

    val config11 = findOrCreateConfiguration(location)
    assertIsTheSame(config1, config11)
  }

  def testCreateFromTopLevelWhitespace_ShouldSelectFirstMainInFile_FirstIsObjectMainMethod(): Unit = {
    val pack = "org.example"
    val packagePrefix = pack + "."

    val vFile = addFileToProjectSources(Some(pack), "FileWithMain.scala",
      s"""$PackageStatementPlaceholder
         |//location
         |
         |object MyMainObject1 {
         |  def main(args: Array[String]): Unit = {}
         |}
         |
         |@main def mainTopLevel1(): Unit = println(42)
         |
         |object MyMainObject2 {
         |  def main(args: Array[String]): Unit = {}
         |}
         |
         |@main def mainTopLevel2(): Unit = println(42)
         |""".stripMargin
    )

    val location = CaretLocation2(vFile, 1, 0)
    val config1 = doTest(location, "MyMainObject1", packagePrefix + "MyMainObject1")

    saveConfigurationsInRunManager(config1)

    val config11 = findOrCreateConfiguration(location)
    assertIsTheSame(config1, config11)
  }

  def testMainAnnotated_InNestedPackaging(): Unit = {
    val pack = "org.example"
    val packagePrefix = pack + "."

    val vFile = addFileToProjectSources(Some(pack), "FileWithMain.scala",
      s"""$PackageStatementPlaceholder
         |
         |@main def myMainA(): Unit = {}
         |
         |package b {
         |  @main def myMainB(): Unit = {}
         |  //locationA2
         |
         |  package c {
         |    @main def myMainC(): Unit = {}
         |    //locationB2
         |
         |  }
         |}
         |""".stripMargin
    )

    val locationA1 = CaretLocation2(vFile, 2, 10)
    val locationA2 = CaretLocation2(vFile, 3, 0)

    val locationB1 = CaretLocation2(vFile, 5, 10)
    val locationB2 = CaretLocation2(vFile, 6, 0)
    val locationB3 = CaretLocation2(vFile, 7, 0)

    val locationC1 = CaretLocation2(vFile, 9, 10)
    val locationC2 = CaretLocation2(vFile, 10, 0)
    val locationC3 = CaretLocation2(vFile, 11, 0)

    val configA = doTest(locationA1, "myMainA", packagePrefix + "myMainA")
    val configB = doTest(locationB1, "myMainB", packagePrefix + "b.myMainB")
    val configC = doTest(locationC1, "myMainC", packagePrefix + "b.c.myMainC")

    saveConfigurationsInRunManager(
      configA,
      configB,
      configC,
    )

    val configA1Reused = findOrCreateConfiguration(locationA1)
    val configA2Reused = findOrCreateConfiguration(locationA2)

    val configB1Reused = findOrCreateConfiguration(locationB1)
    val configB2Reused = findOrCreateConfiguration(locationB2)
    val configB3Reused = findOrCreateConfiguration(locationB3)

    val configC1Reused = findOrCreateConfiguration(locationC1)
    val configC2Reused = findOrCreateConfiguration(locationC2)
    val configC3Reused = findOrCreateConfiguration(locationC3)

    assertIsTheSame(configA, configA1Reused)
    assertIsTheSame(configA, configA2Reused)

    assertIsTheSame(configB, configB1Reused)
    assertIsTheSame(configB, configB2Reused)
    assertIsTheSame(configB, configB3Reused)

    assertIsTheSame(configC, configC1Reused)
    assertIsTheSame(configC, configC2Reused)
    assertIsTheSame(configC, configC3Reused)
  }

}