package org.jetbrains.bsp.projectHighlighting

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import org.jetbrains.bsp.BSP
import org.jetbrains.bsp.project.importing.setup.SbtConfigSetup
import org.jetbrains.bsp.settings.BspProjectSettings
import org.jetbrains.plugins.scala.build.{BuildMessages, ConsoleReporter}
import org.jetbrains.plugins.scala.extensions.RichFile
import org.jetbrains.plugins.scala.projectHighlighting.base.ProjectHighlightingTestUtils
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.ScalaExternalSystemImportingTestBase
import org.junit.Assert.fail

import java.io.File
import scala.util.Try

/** See also [[org.jetbrains.sbt.project.SbtExternalSystemImportingTestLike]] */
trait SbtOverBspExternalSystemImportingTestCase extends ScalaExternalSystemImportingTestBase {

  //To open SBT project as BSP you still need `build.sbt` file
  override protected def getExternalSystemConfigFileName: String = Sbt.BuildFile

  override protected def getExternalSystemId: ProjectSystemId = BSP.ProjectSystemId

  override protected def getTestsTempDir: String = "" // Use default temp directory

  override protected def getCurrentExternalProjectSettings: BspProjectSettings = new BspProjectSettings

  override def setUpFixtures(): Unit = {
    super.setUpFixtures()

    //need to do this before actual import is started in `setUp` method
    ProjectHighlightingTestUtils.dontPrintErrorsAndWarningsToConsole(this)

    generateSbtBspConfigurationFileIfNeeded()
  }

  protected def generateSbtBspConfigurationFileIfNeeded(): Unit = {
    val projectPath = new File(this.getTestProjectPath)
    val bspConfigFile = projectPath / ".bsp/sbt.json"

    //NOTE: we could extract a setting to reuse or not to reuse bsp config file
    if (!bspConfigFile.exists()) {
      generateSbtBspConfigurationFile(projectPath)
    } else {
      println(
        s"""!!!
           |!!! Reusing existing BSP connection configuration file ${bspConfigFile}
           |!!! """.stripMargin
      )
    }
  }

  protected def generateSbtBspConfigurationFile(projectPath: File): Unit = {
    val title = "Generating sbt bsp configuration"
    println(s"$title Started")

    //it's done in `setupSdk` but in this test we need JDK earlier
    setupProjectJdk()
    val jdk = getJdkConfiguredForTestCase
    val sbtBspConfigSetup = SbtConfigSetup(projectPath, jdk)
    val reporter = new ConsoleReporter(name = "") {
      override def progressTask(eventId: BuildMessages.EventId, total: Long, progress: Long, unit: String, message: String, time: Long): Unit = {
        //do nothing, in tests it's enough to see the console output which is already printed by SbtStructureDump
      }
    }
    val buildMessages: Try[BuildMessages] = sbtBspConfigSetup.run(reporter)
    val messages = buildMessages.get
    if (messages.errors.nonEmpty) {
      fail(s"$title Failed")
    }
    else {
      println(s"$title Completed")
    }
  }
}