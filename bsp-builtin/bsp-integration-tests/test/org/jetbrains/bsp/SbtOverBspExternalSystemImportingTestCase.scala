package org.jetbrains.bsp

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.progress.{ProgressIndicator, Task}
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.bsp.project.importing.setup.SbtConfigSetup
import org.jetbrains.bsp.settings.BspProjectSettings
import org.jetbrains.plugins.scala.build.{BuildMessages, ConsoleReporter}
import org.jetbrains.plugins.scala.extensions.{PathExt, inWriteAction}
import org.jetbrains.sbt.{Sbt, SbtVersion}
import org.jetbrains.sbt.project.{SbtProjectImportTestUtils, ScalaExternalSystemImportingTestBase}

import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import scala.util.{Failure, Success}

/** See also [[org.jetbrains.sbt.project.SbtExternalSystemImportingTestLike]] */
trait SbtOverBspExternalSystemImportingTestCase extends ScalaExternalSystemImportingTestBase {

  //To open SBT project as BSP you still need `build.sbt` file
  override protected def getExternalSystemConfigFileName: String = Sbt.BuildFile

  override protected def getExternalSystemId: ProjectSystemId = BSP.ProjectSystemId

  override protected def getTestsTempDir: String = "" // Use default temp directory

  final override protected lazy val getCurrentExternalProjectSettings: BspProjectSettings =
    new BspProjectSettings

  protected def reuseExistingConnectionFile: Boolean = true

  /**
   * sbt version that should be injected into the `build.properties` file in the project.
   *
   * @see [[injectSbtVersion]]
   */
  protected def sbtVersionToInject: Option[SbtVersion] = None

  override protected def setupBeforeProjectImport(): Unit = {
    super.setupBeforeProjectImport()

    //need to do this before actual import is started in `setUp` method
    SbtProjectImportTestUtils.suppressSbtStructureDumpErrorAndWarningConsoleOutput(this)

    injectSbtVersion()
    generateSbtBspConfigurationFileIfNeeded()
  }

  protected def injectSbtVersion(): Unit =
    sbtVersionToInject.foreach { version =>
      SbtProjectImportTestUtils.injectVariable(
        getTestProjectPath / "project" / "build.properties",
        "$SBT_VERSION$",
        version.minor,
      )
    }

  protected def generateSbtBspConfigurationFileIfNeeded(): Unit = {
    val projectPath = getTestProjectPath
    val bspConfigFile = projectPath / ".bsp/sbt.json"

    if (!bspConfigFile.exists || !reuseExistingConnectionFile) {
      generateSbtBspConfigurationFile(projectPath)
    } else {
      println(
        s"""!!!
           |!!! Reusing existing BSP connection configuration file $bspConfigFile
           |!!! """.stripMargin
      )
    }
  }

  /**
   * Optional JDK to set as JAVA_HOME when generating the BSP connection file.
   *
   * Context:
   * In sbt/BSP projects, the module SDK is determined first based on
   * `JvmBuildTarget#javaHome` ([[org.jetbrains.bsp.data.BspMetadataService#doImport]]).
   *
   * In sbt, `JvmBuildTarget#javaHome` is calculated from the `javaHome` setting.
   * If it is not set, it falls back to the JDK used to run the BSP server.
   * Since the current sbt/BSP tests do not set `javaHome` setting, `JvmBuildTarget#javaHome` depends
   * on the JDK used by the server. The BSP server uses the JDK defined in the connection file.
   *
   * sbt 1.x
   *
   * The JDK written to the connection file is taken from the `java.home` system property,
   * which is the JDK used when generating the connection file. So if the JDK used to generate
   * the file is the JDK configured for the test, this JDK will be used as the project/module SDK.
   * So, tests with sbt 1.x don't need to set a custom JAVA_HOME.
   * Links:
   *  - https://github.com/sbt/sbt/blob/df1243400eedce4cbd314c61965ee798ee1b18f9/protocol/src/main/scala/sbt/internal/bsp/BuildServerConnection.scala#L27
   *  - https://github.com/sbt/sbt/blob/df1243400eedce4cbd314c61965ee798ee1b18f9/internal/util-collection/src/main/scala/sbt/internal/util/Util.scala#L116
   *
   * sbt 2.x
   *
   * It works differently from sbt 1.x. The JDK written to the connection file is taken
   * from the `JAVA_HOME` environment variable. Only if `JAVA_HOME` is not
   * defined sbt falls back to the `java.home` system property.
   * Since `JAVA_HOME` on CI machines may change over time, relying on it would make the tests
   * non-deterministic. Therefore, sbt 2.x BSP tests that verify project/module SDK should
   * explicitly set `JAVA_HOME` to ensure that the expected JDK is written to the BSP connection file.
   * Links:
   *   - https://github.com/sbt/sbt/blob/5ed34be48c791b6b760aec17fe30333a595e63ff/internal/util-core/src/main/scala/sbt/internal/util/Util.scala#L85
   */
  protected def jdkForBspConnectionFile: Option[Sdk] = None

  protected def generateSbtBspConfigurationFile(projectPath: Path): Unit = {
    val title = "Generating sbt bsp configuration"
    println(s"$title Started")

    //it's done in `setupSdk` but in this test we need JDK earlier
    setupProjectJdk()
    val jdk = getJdkConfiguredForTestCase

    val jdkHome = jdkForBspConnectionFile.map(_.getHomePath)
    val environment = jdkHome.map(home => Map("JAVA_HOME" -> home)).getOrElse(Map.empty)

    val future = new CompletableFuture[Unit]()
    val task = new Task.Backgroundable(null, title, false) {
      override def run(indicator: ProgressIndicator): Unit = {
        val sbtBspConfigSetup = SbtConfigSetup(projectPath, jdk, environment)
        val reporter = new ConsoleReporter(name = "") {
          override def progressTask(eventId: BuildMessages.EventId, total: Long, progress: Long, unit: String, message: String, time: Long): Unit = {
            //do nothing, in tests it's enough to see the console output which is already printed by SbtStructureDump
          }
        }
        val buildMessages = sbtBspConfigSetup.run(indicator)(using reporter)
        buildMessages match {
          case Failure(exception) =>
            future.completeExceptionally(exception)
          case Success(messages) =>
            if (messages.errors.nonEmpty) {
              future.completeExceptionally(new AssertionError(s"$title Failed: ${messages.errors.map(_.getMessage).mkString("\n")}"))
            } else {
              println(s"$title Completed")
              future.complete(())
            }
        }
      }
    }
    task.queue()
    PlatformTestUtil.waitForFuture(future)
  }

  override def tearDown(): Unit = {
    inWriteAction {
      val table = ProjectJdkTable.getInstance
      table.getAllJdks.foreach(table.removeJdk)
    }
    super.tearDown()
  }
}
