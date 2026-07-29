package org.jetbrains.plugins.scala.compiler.buildtools

import com.intellij.maven.testFramework.fixtures.{MavenTestFixtureFoldersKt, MavenTestFixtureProjectKt}
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.{ModuleRootModificationUtil, ProjectRootManager}
import com.intellij.testFramework.CompilerTester
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.compiler.TestJdkVersionArguments
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.compiler.testUtils.CompileServerTestUtil
import org.jetbrains.plugins.scala.project.maven.ScalaMavenImporterTestBase
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.util.runners.TestJdkVersion
import org.junit.jupiter.api.Assertions.{assertNotNull, assertTrue}
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

import java.nio.file.Path
import scala.jdk.CollectionConverters.*

@ParameterizedClass
@ArgumentsSource(classOf[TestJdkVersionArguments])
class MavenProjectWithPureJavaModuleTest(jdkVersion: TestJdkVersion) extends ScalaMavenImporterTestBase(None):

  @Test
  def importAndCompile_Zinc(): Unit =
    runImportAndCompileTest(IncrementalityType.SBT)

  @Test
  def importAndCompile_IDEA(): Unit =
    runImportAndCompileTest(IncrementalityType.IDEA)

  private def runImportAndCompileTest(incrementality: IncrementalityType): Unit =
    CompileServerTestUtil.registerLongRunningThreads()

    withCompileServerJdk { sdk =>
      setUpTestProject()

      importProjects(mavenTestFixture.getProjectPom)

      ScalaCompilerConfiguration.instanceIn(getProject).incrementalityType = incrementality

      val modules = ModuleManager.getInstance(getProject).getModules
      modules.foreach(ModuleRootModificationUtil.setModuleSdk(_, sdk))

      withCompiler { compiler =>
        val jdk21warnings = Set(
          "source value 8 is obsolete and will be removed in a future release",
          "target value 8 is obsolete and will be removed in a future release",
          "To suppress warnings about obsolete options, use -Xlint:-options"
        )

        val bootstrapClasspathWarnings = incrementality match
          case IncrementalityType.SBT => Set("bootstrap class path not set in conjunction with -source 8")
          case IncrementalityType.IDEA => Set.empty

        val messages = compiler.make()
        val errorsAndWarnings = messages.asScala.filter { message =>
          val category = message.getCategory
          category == CompilerMessageCategory.ERROR || category == CompilerMessageCategory.WARNING
        }.filterNot(msg => jdk21warnings.exists(s => msg.getMessage.contains(s)))
          .filterNot(msg => bootstrapClasspathWarnings.exists(s => msg.getMessage.contains(s)))

        assertTrue(
          errorsAndWarnings.isEmpty,
          s"Expected no compilation errors or warnings, got: ${errorsAndWarnings.mkString(System.lineSeparator())}"
        )

        val module1 = modules.find(_.getName == "module1").orNull
        assertNotNull(module1, "Could not find module with name 'module1'")
        val module2 = modules.find(_.getName == "module2").orNull
        assertNotNull(module2, "Could not find module with name 'module2'")

        val greeter = compiler.findClassFilePath("Greeter", module1)
        assertNotNull(greeter, "Could not find compiled class file Greeter")

        val helloWorldGreeter = compiler.findClassFilePath("HelloWorldGreeter", module2)
        assertNotNull(helloWorldGreeter, "Could not find compiled class file HelloWorldGreeter")

        val helloWorldGreeterModule = compiler.findClassFilePath("HelloWorldGreeter$", module2)
        assertNotNull(helloWorldGreeterModule, "Could not find compiled class file HelloWorldGreeter$")
      }
    }
  end runImportAndCompileTest

  /**
   * Sets up the JDK as the project SDK and for the Scala compile server around `test`, and removes it afterwards.
   * SmartJDKLoader registers the JDK in the application-level table without a disposable,
   * so it must be removed manually.
   */
  private def withCompileServerJdk(test: Sdk => Unit): Unit =
    val sdk = WriteAction.computeAndWait: () =>
      val sdk = SmartJDKLoader.getOrCreateJDK(jdkVersion.toProductionVersion)
      ProjectRootManager.getInstance(getProject).setProjectSdk(sdk)
      sdk

    val settings = ScalaCompileServerSettings.getInstance()
    settings.COMPILE_SERVER_SDK = sdk.getName
    settings.USE_DEFAULT_SDK = false

    try test(sdk)
    finally
      settings.USE_DEFAULT_SDK = true
      settings.COMPILE_SERVER_SDK = null
      WriteAction.runAndWait(() => ProjectJdkTable.getInstance().removeJdk(sdk))

  private def setUpTestProject(): Unit =
    MavenTestFixtureFoldersKt.createProjectSubDirs(mavenTestFixture, "module1/src/main/java", "module2/src/main/scala")
    MavenTestFixtureProjectKt.createProjectPom(mavenTestFixture,
      """    <groupId>org.example</groupId>
        |    <artifactId>pure-java</artifactId>
        |    <packaging>pom</packaging>
        |    <version>1.0-SNAPSHOT</version>
        |
        |    <modules>
        |        <module>module1</module>
        |        <module>module2</module>
        |    </modules>
        |""".stripMargin,
      false,
    )
    MavenTestFixtureProjectKt.createModulePom(mavenTestFixture, "module1",
      """    <!-- parent pom -->
        |    <parent>
        |        <groupId>org.example</groupId>
        |        <artifactId>pure-java</artifactId>
        |        <version>1.0-SNAPSHOT</version>
        |    </parent>
        |
        |    <artifactId>module1</artifactId>
        |    <version>1.0-SNAPSHOT</version>
        |    <packaging>jar</packaging>
        |
        |    <properties>
        |        <maven.compiler.source>1.8</maven.compiler.source>
        |        <maven.compiler.target>1.8</maven.compiler.target>
        |    </properties>
        |
        |    <build>
        |        <plugins>
        |            <plugin>
        |                <groupId>org.apache.maven.plugins</groupId>
        |                <artifactId>maven-compiler-plugin</artifactId>
        |                <version>3.11.0</version>
        |            </plugin>
        |        </plugins>
        |    </build>""".stripMargin,
      false,
    )
    MavenTestFixtureProjectKt.createModulePom(mavenTestFixture, "module2",
      """<!-- parent pom -->
        |    <parent>
        |        <groupId>org.example</groupId>
        |        <artifactId>pure-java</artifactId>
        |        <version>1.0-SNAPSHOT</version>
        |    </parent>
        |
        |    <artifactId>module2</artifactId>
        |    <version>1.0-SNAPSHOT</version>
        |    <packaging>jar</packaging>
        |
        |    <properties>
        |        <maven.compiler.source>1.8</maven.compiler.source>
        |        <maven.compiler.target>1.8</maven.compiler.target>
        |    </properties>
        |
        |    <build>
        |        <sourceDirectory>src/main/scala</sourceDirectory>
        |        <plugins>
        |            <plugin>
        |                <groupId>org.apache.maven.plugins</groupId>
        |                <artifactId>maven-compiler-plugin</artifactId>
        |                <version>3.11.0</version>
        |            </plugin>
        |            <plugin>
        |                <groupId>net.alchim31.maven</groupId>
        |                <artifactId>scala-maven-plugin</artifactId>
        |                <version>4.8.1</version>
        |                <executions>
        |                    <execution>
        |                        <goals>
        |                            <goal>compile</goal>
        |                            <goal>testCompile</goal>
        |                        </goals>
        |                    </execution>
        |                </executions>
        |                <configuration>
        |                    <scalaVersion>2.13.12</scalaVersion>
        |                </configuration>
        |            </plugin>
        |        </plugins>
        |    </build>
        |
        |    <dependencies>
        |        <dependency>
        |            <groupId>org.example</groupId>
        |            <artifactId>module1</artifactId>
        |            <version>1.0-SNAPSHOT</version>
        |        </dependency>
        |    </dependencies>""".stripMargin,
      false,
    )
    MavenTestFixtureProjectKt.createProjectSubFile(mavenTestFixture, "module1/src/main/java/Greeter.java",
      """interface Greeter {
        |  String greeting();
        |}
        |""".stripMargin)
    MavenTestFixtureProjectKt.createProjectSubFile(mavenTestFixture, "module2/src/main/scala/HelloWorldGreeter.scala",
      """object HelloWorldGreeter extends Greeter {
        |  def greeting: String = "Hello, world!"
        |}
        |""".stripMargin)
  end setUpTestProject

  private def withCompiler(action: CompilerTester => Unit): Unit =
    val project = getProject
    val modules = ModuleManager.getInstance(project).getModules
    val compiler = CompilerTester(project, java.util.Arrays.asList(modules*), null, false)
    try action(compiler)
    finally compiler.tearDown()

  extension (compiler: CompilerTester)
    @Nullable
    //noinspection SSBasedInspection
    private def findClassFilePath(@NotNull className: String, @NotNull module: Module): Path =
      compiler.findClassFile(className, module) match
        case null => null
        case file => file.toPath

end MavenProjectWithPureJavaModuleTest
