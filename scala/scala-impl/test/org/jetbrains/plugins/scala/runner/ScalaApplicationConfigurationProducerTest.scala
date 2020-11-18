package org.jetbrains.plugins.scala.runner

import java.io.File

import com.intellij.execution.RunManager
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.impl.{RunConfigurationLevel, RunManagerImpl, RunnerAndConfigurationSettingsImpl}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.psi.{PsiElement, PsiManager}
import org.jetbrains.plugins.scala.AssertionMatchers.AssertMatchersExt
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.testingSupport.CaretLocation

class ScalaApplicationConfigurationProducerTest extends ScalaFixtureTestCaseWithSourceFolder {

  private def configurationProducer = ScalaApplicationConfigurationProducer()

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3_0

  protected def createConfiguration(location: CaretLocation): ApplicationConfiguration = {
    val context = configurationContext(location)
    val configuration = configurationProducer.createConfigurationFromContext(context)
    configuration.getConfiguration.asInstanceOf[ApplicationConfiguration]
  }

  protected def findOrCreateConfiguration(location: CaretLocation): ApplicationConfiguration = {
    val context = configurationContext(location)
    val configuration = configurationProducer.findOrCreateConfigurationFromContext(context)
    configuration.getConfiguration.asInstanceOf[ApplicationConfiguration]
  }

  protected def configurationContext(location: CaretLocation): ConfigurationContext = {
    val psiElement = findPsiElement(location)
    new ConfigurationContext(psiElement)
  }

  /**
   * @todo this duplicates [[org.jetbrains.plugins.scala.testingSupport.ScalaTestingTestCase#createPsiLocation(CaretLocation)]]<br>
   *       extract some common base class / utility method / trait
   */
  protected def findPsiElement(location: CaretLocation): PsiElement = {
    val srcDir = new File(getSourceRootDir.toNioPath.toUri)
    val ioFile = new File(srcDir, location.fileName)

    val file = getVirtualFile(ioFile)

    val project = getProject

    val myManager = PsiManager.getInstance(project)

    inReadAction {
      val psiFile = myManager.findViewProvider(file).getPsi(scalaLanguage)
      val document = FileDocumentManager.getInstance().getDocument(file)
      val lineStartOffset = document.getLineStartOffset(location.line)
      psiFile.findElementAt(lineStartOffset + location.column)
    }
  }

  private def getVirtualFile(file: File): VirtualFile =
    LocalFileSystem.getInstance.refreshAndFindFileByIoFile(file)

  protected def doTest(location: CaretLocation, configName: String, mainClassName: String): ApplicationConfiguration = {
    val configuration = createConfiguration(location)
    configuration.getName shouldBe configName
    configuration.getMainClassName shouldBe mainClassName
    configuration
  }

  private val PackageStatementPlaceholder = "<package>"

  private def addFileToProjectSources(pack: Option[String], fileName: String, fileText: String): String ={
    val filePrefix = pack.fold("")(_.replace('.', '/') + "/")
    val packageStatement = pack.fold("")(p => s"package $p")

    val fileRelativePath = filePrefix + fileName
    val fileTextWithFixedPackage = fileText.replace(PackageStatementPlaceholder, packageStatement)
    addFileToProjectSources(fileRelativePath, fileTextWithFixedPackage)
    fileRelativePath
  }

  private def commonTestScala3AnnotatedMainFunction(pack: Option[String]): Unit = {
    val packagePrefix = pack.fold("")(_ + ".")

    val fileName = addFileToProjectSources(pack, "topLevelFunctions.scala",
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

    doTest(CaretLocation(fileName, 3, 10), "mainFoo", packagePrefix + "mainFoo")
    doTest(CaretLocation(fileName, 7, 10), "mainFooWithCustomParams", packagePrefix + "mainFooWithCustomParams")
    doTest(CaretLocation(fileName, 11, 10), "mainFooWithCustomParamsWithVararg", packagePrefix + "mainFooWithCustomParamsWithVararg")
    doTest(CaretLocation(fileName, 15, 10), "mainFooWithoutParams", packagePrefix + "mainFooWithoutParams")
  }

  def testScala3AnnotatedMainFunction(): Unit = {
    commonTestScala3AnnotatedMainFunction(Some("org.example"))
  }

  def testScala3AnnotatedMainFunction_RootPackage(): Unit = {
    commonTestScala3AnnotatedMainFunction(None)
  }

  def testScala3AnnotatedMainFunction_InObject(): Unit = {
    val pack = "org.example2"
    val packagePrefix = pack + "."

    val fileName = addFileToProjectSources(Some(pack), "MyObject1.scala",
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

    doTest(CaretLocation(fileName, 4, 10), "mainFooInObject1", packagePrefix + "mainFooInObject1")
    doTest(CaretLocation(fileName, 9, 10), "mainFooInObject2", packagePrefix + "mainFooInObject2")
  }

  def testScala3AnnotatedMainFunction_InSameFileWithOrdinaryMainFunction(): Unit = {
    val pack = "org.example3"
    val packagePrefix = pack + "."

    val fileName = addFileToProjectSources(Some(pack), "topLevelDefinitions.scala",
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

    val location1 = CaretLocation(fileName, 3, 10)
    val location2 = CaretLocation(fileName, 7, 10)
    val location3 = CaretLocation(fileName, 10, 10)
    val location4 = CaretLocation(fileName, 15, 10)
    val location5 = CaretLocation(fileName, 17, 10)
    val location6 = CaretLocation(fileName, 21, 10)

    val config1 = doTest(location1, "mainFoo1", packagePrefix + "mainFoo1")
    val config2 = doTest(location2, "mainFoo2", packagePrefix + "mainFoo2")
    val config3 = doTest(location3, "MyObject1", packagePrefix + "MyObject1")
    val config4 = doTest(location4, "MyObject2", packagePrefix + "MyObject2")
    val config5 = doTest(location5, "MyObject2", packagePrefix + "MyObject2")
    val config6 = doTest(location6, "mainFoo3InObject", packagePrefix + "mainFoo3InObject")

    val manager = RunManager.getInstance(getProject).asInstanceOf[RunManagerImpl]
    Seq(
      config1,
      config2,
      config3,
      config4,
      //config5, // skipping cause should be the same as for config4
      config6
    ).foreach { config =>
      val configSettings = new RunnerAndConfigurationSettingsImpl(manager, config, false, RunConfigurationLevel.PROJECT)
      manager.addConfiguration(configSettings)
    }

    val config11 = findOrCreateConfiguration(location1)
    val config22 = findOrCreateConfiguration(location2)
    val config33 = findOrCreateConfiguration(location3)
    val config44 = findOrCreateConfiguration(location4)
    val config55 = findOrCreateConfiguration(location5)
    val config66 = findOrCreateConfiguration(location6)

    config11 shouldBe config1
    config22 shouldBe config2
    config33 shouldBe config3
    config44 shouldBe config4
    config55 shouldBe config4 // config created when right clicking on main method should be the same when clicking on object itself
    config66 shouldBe config6
  }
}