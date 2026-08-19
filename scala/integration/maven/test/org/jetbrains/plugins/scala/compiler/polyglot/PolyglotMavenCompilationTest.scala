package org.jetbrains.plugins.scala.compiler.polyglot

import com.intellij.maven.testFramework.fixtures.{MavenTestFixtureFoldersKt, MavenTestFixtureProjectKt}
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.{ModuleRootModificationUtil, ProjectRootManager}
import com.intellij.testFramework.{CompilerTester, IndexingTestUtil}
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.compiler.TestJdkVersionArguments
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.compiler.testUtils.CompileServerTestUtil
import org.jetbrains.plugins.scala.project.maven.ScalaMavenImporterTestBase
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.util.runners.TestJdkVersion
import org.junit.jupiter.api.Assertions.{assertEquals, assertNotNull, assertTrue}
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

import scala.jdk.CollectionConverters.*

@ParameterizedClass
@ArgumentsSource(classOf[TestJdkVersionArguments])
class PolyglotMavenCompilationTest(jdkVersion: TestJdkVersion) extends ScalaMavenImporterTestBase(None):

  @Test
  def polyglotCompilation(): Unit =
    CompileServerTestUtil.registerLongRunningThreads()

    withCompileServerJdk { sdk =>
      setUpTestProject()

      importProjects(mavenTestFixture.getProjectPom)

      KotlinDaemonUtil.disableKotlinDaemon(getProject)

      val modules = ModuleManager.getInstance(getProject).getModules
      modules.foreach(ModuleRootModificationUtil.setModuleSdk(_, sdk))

      IndexingTestUtil.waitUntilIndexesAreReady(getProject)

      assertEquals(IncrementalityType.SBT, ScalaCompilerConfiguration.instanceIn(getProject).incrementalityType)
      val module1 = findModule("module1")
      val module2 = findModule("module2")

      withCompiler { compiler =>
        val messages = compiler.make()
        val errors = messages.asScala.filter(_.getCategory == CompilerMessageCategory.ERROR)
        assertTrue(errors.isEmpty, s"Expected no compilation errors, got: ${errors.mkString(System.lineSeparator())}")

        assertClassExists(compiler, "Greeter", module1)
        assertClassExists(compiler, "AbstractGreeter", module1)
        assertClassExists(compiler, "HelloWorldGreeter", module2)
      }
    }

  /**
   * Sets up the JDK as the project SDK and for the Scala compile server around `test`, and removes it afterwards,
   * together with the "Kotlin SDK" registered by the Kotlin Maven import. SmartJDKLoader and the Kotlin importer
   * register them in the application-level table without a disposable, so they must be removed manually.
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
      WriteAction.runAndWait { () =>
        val jdkTable = ProjectJdkTable.getInstance()
        jdkTable.removeJdk(sdk)
        val kotlinSdk = jdkTable.getAllJdks.find(_.getName.contains("Kotlin SDK"))
        kotlinSdk.foreach(jdkTable.removeJdk)
      }

  private def setUpTestProject(): Unit =
    MavenTestFixtureFoldersKt.createProjectSubDirs(mavenTestFixture, "module1/src/main/java", "module1/src/main/kotlin", "module2/src/main/scala")
    MavenTestFixtureProjectKt.createProjectPom(mavenTestFixture,
      """<groupId>org.example</groupId>
        |<artifactId>polyglot-maven</artifactId>
        |<packaging>pom</packaging>
        |<version>1.0-SNAPSHOT</version>
        |
        |<modules>
        |  <module>module1</module>
        |  <module>module2</module>
        |</modules>
        |
        |<!-- Prefer the JetBrains Maven Central mirror to avoid HTTP Error 429 Too Many Requests in the CI.
        |     Maven's implicit central repository remains as a fallback. -->
        |<repositories>
        |  <repository>
        |    <id>jetbrains-maven-central-mirror</id>
        |    <url>https://cache-redirector.jetbrains.com/maven-central/</url>
        |  </repository>
        |</repositories>
        |
        |<pluginRepositories>
        |  <pluginRepository>
        |    <id>jetbrains-maven-central-mirror</id>
        |    <url>https://cache-redirector.jetbrains.com/maven-central/</url>
        |  </pluginRepository>
        |</pluginRepositories>
        |""".stripMargin,
      false,
    )
    MavenTestFixtureProjectKt.createModulePom(mavenTestFixture, "module1",
      """<!-- parent pom -->
        |<parent>
        |  <groupId>org.example</groupId>
        |  <artifactId>polyglot-maven</artifactId>
        |  <version>1.0-SNAPSHOT</version>
        |</parent>
        |
        |<artifactId>module1</artifactId>
        |<version>1.0-SNAPSHOT</version>
        |<packaging>jar</packaging>
        |
        |<properties>
        |  <maven.compiler.source>1.8</maven.compiler.source>
        |  <maven.compiler.target>1.8</maven.compiler.target>
        |  <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        |  <kotlin.code.style>official</kotlin.code.style>
        |  <kotlin.compiler.jvmTarget>1.8</kotlin.compiler.jvmTarget>
        |  <kotlin.version>2.0.21</kotlin.version>
        |</properties>
        |
        |<dependencies>
        |  <dependency>
        |    <groupId>org.jetbrains.kotlin</groupId>
        |    <artifactId>kotlin-stdlib</artifactId>
        |    <version>${kotlin.version}</version>
        |  </dependency>
        |</dependencies>
        |
        |<repositories>
        |  <repository>
        |    <id>jetbrains-maven-central-mirror</id>
        |    <url>https://cache-redirector.jetbrains.com/maven-central/</url>
        |  </repository>
        |  <repository>
        |    <id>mavenCentral</id>
        |    <url>https://repo1.maven.org/maven2/</url>
        |  </repository>
        |</repositories>
        |
        |<build>
        |    <plugins>
        |        <plugin>
        |            <groupId>org.jetbrains.kotlin</groupId>
        |            <artifactId>kotlin-maven-plugin</artifactId>
        |            <version>${kotlin.version}</version>
        |            <extensions>true</extensions>
        |            <executions>
        |                <execution>
        |                    <id>compile</id>
        |                    <goals>
        |                        <goal>compile</goal> <!-- You can skip the <goals> element
        |                        if you enable extensions for the plugin -->
        |                    </goals>
        |                    <configuration>
        |                        <sourceDirs>
        |                            <sourceDir>${project.basedir}/src/main/kotlin</sourceDir>
        |                            <sourceDir>${project.basedir}/src/main/java</sourceDir>
        |                        </sourceDirs>
        |                    </configuration>
        |                </execution>
        |                <execution>
        |                    <id>test-compile</id>
        |                    <goals>
        |                        <goal>test-compile</goal> <!-- You can skip the <goals> element
        |                    if you enable extensions for the plugin -->
        |                    </goals>
        |                    <configuration>
        |                        <sourceDirs>
        |                            <sourceDir>${project.basedir}/src/test/kotlin</sourceDir>
        |                            <sourceDir>${project.basedir}/src/test/java</sourceDir>
        |                        </sourceDirs>
        |                    </configuration>
        |                </execution>
        |            </executions>
        |        </plugin>
        |        <plugin>
        |            <groupId>org.apache.maven.plugins</groupId>
        |            <artifactId>maven-compiler-plugin</artifactId>
        |            <version>3.13.0</version>
        |            <executions>
        |                <!-- Replacing default-compile as it is treated specially by Maven -->
        |                <execution>
        |                    <id>default-compile</id>
        |                    <phase>none</phase>
        |                </execution>
        |                <!-- Replacing default-testCompile as it is treated specially by Maven -->
        |                <execution>
        |                    <id>default-testCompile</id>
        |                    <phase>none</phase>
        |                </execution>
        |                <execution>
        |                    <id>java-compile</id>
        |                    <phase>compile</phase>
        |                    <goals>
        |                        <goal>compile</goal>
        |                    </goals>
        |                </execution>
        |                <execution>
        |                    <id>java-test-compile</id>
        |                    <phase>test-compile</phase>
        |                    <goals>
        |                        <goal>testCompile</goal>
        |                    </goals>
        |                </execution>
        |            </executions>
        |        </plugin>
        |    </plugins>
        |</build>
        |""".stripMargin,
      false,
    )
    MavenTestFixtureProjectKt.createModulePom(mavenTestFixture, "module2",
      """<!-- parent pom -->
        |<parent>
        |  <groupId>org.example</groupId>
        |  <artifactId>polyglot-maven</artifactId>
        |  <version>1.0-SNAPSHOT</version>
        |</parent>
        |
        |<artifactId>module2</artifactId>
        |<version>1.0-SNAPSHOT</version>
        |<packaging>jar</packaging>
        |
        |<properties>
        |  <scala.version>2.13.15</scala.version>
        |</properties>
        |
        |<dependencies>
        |  <dependency>
        |    <groupId>org.example</groupId>
        |    <artifactId>module1</artifactId>
        |    <version>1.0-SNAPSHOT</version>
        |  </dependency>
        |</dependencies>
        |
        |<build>
        |  <sourceDirectory>src/main/scala</sourceDirectory>
        |  <plugins>
        |    <plugin>
        |      <groupId>net.alchim31.maven</groupId>
        |      <artifactId>scala-maven-plugin</artifactId>
        |      <version>4.9.2</version>
        |      <configuration>
        |        <scalaVersion>${scala.version}</scalaVersion>
        |      </configuration>
        |    </plugin>
        |  </plugins>
        |</build>
        |""".stripMargin,
      false,
    )
    MavenTestFixtureProjectKt.createProjectSubFile(mavenTestFixture, "module1/src/main/java/Greeter.java",
      """public interface Greeter {
        |  String greeting();
        |}
        |""".stripMargin)
    MavenTestFixtureProjectKt.createProjectSubFile(mavenTestFixture, "module1/src/main/kotlin/AbstractGreeter.kt",
      """abstract class AbstractGreeter(private val str: String) : Greeter {
        |  override fun greeting(): String = str
        |}
        |""".stripMargin)
    MavenTestFixtureProjectKt.createProjectSubFile(mavenTestFixture, "module2/src/main/scala/HelloWorldGreeter.scala",
      """object HelloWorldGreeter extends AbstractGreeter("Hello, world!")
        |""".stripMargin)
  end setUpTestProject

  //noinspection SSBasedInspection
  private def assertClassExists(compiler: CompilerTester, name: String, module: Module): Unit =
    val file = compiler.findClassFile(name, module)
    assertNotNull(file, s"Could not find class file for $name")

  private def findModule(name: String): Module =
    val modules = ModuleManager.getInstance(getProject).getModules
    val m = modules.find(_.getName == name).orNull
    assertNotNull(m, s"Could not find module with name '$name'")
    m

  private def withCompiler(action: CompilerTester => Unit): Unit =
    val project = getProject
    val modules = ModuleManager.getInstance(project).getModules
    val compiler = CompilerTester(project, java.util.Arrays.asList(modules*), null, false)
    try action(compiler)
    finally compiler.tearDown()
end PolyglotMavenCompilationTest
