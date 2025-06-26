package org.jetbrains.plugins.scala.compiler.polyglot

import com.intellij.maven.testFramework.MavenImportingTestCase
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.{CompilerTester, IndexingTestUtil}
import junit.framework.TestCase.{assertEquals, assertNotNull}
import org.jetbrains.plugins.scala.CompilationTests_Zinc
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.compiler.{CompileServerTestUtil, JdkVersionDiscovery}
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.junit.experimental.categories.Category

@Category(Array(classOf[CompilationTests_Zinc]))
class PolyglotMavenCompilationTest extends MavenImportingTestCase {

  private var sdk: Sdk = _

  private var compiler: CompilerTester = _

  private var module1: Module = _

  private var module2: Module = _

  override def setUp(): Unit = {
    super.setUp()

    sdk = {
      val jdkVersion = JdkVersionDiscovery.discoveredJdk
      val res = SmartJDKLoader.getOrCreateJDK(jdkVersion)
      val settings = ScalaCompileServerSettings.getInstance()
      settings.COMPILE_SERVER_SDK = res.getName
      settings.USE_DEFAULT_SDK = false
      res
    }

    CompileServerTestUtil.registerLongRunningThreads()

    createProjectSubDirs("module1/src/main/java", "module1/src/main/kotlin", "module2/src/main/scala")
    createProjectPom(
      """<groupId>org.example</groupId>
        |<artifactId>polyglot-maven</artifactId>
        |<packaging>pom</packaging>
        |<version>1.0-SNAPSHOT</version>
        |
        |<modules>
        |  <module>module1</module>
        |  <module>module2</module>
        |</modules>
        |""".stripMargin)
    createModulePom("module1",
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
        |<repositories>
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
        |""".stripMargin)
    createModulePom("module2",
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
        |""".stripMargin)

    createProjectSubFile("module1/src/main/java/Greeter.java",
      """public interface Greeter {
        |  String greeting();
        |}
        |""".stripMargin)
    createProjectSubFile("module1/src/main/kotlin/AbstractGreeter.kt",
      """abstract class AbstractGreeter(private val str: String) : Greeter {
        |  override fun greeting(): String = str
        |}
        |""".stripMargin)
    createProjectSubFile("module2/src/main/scala/HelloWorldGreeter.scala",
      """object HelloWorldGreeter extends AbstractGreeter("Hello, world!")
        |""".stripMargin)

    runWithoutStaticSync()
    importProject()

    KotlinDaemonUtil.disableKotlinDaemon(getProject)

    val modules = ModuleManager.getInstance(getProject).getModules
    modules.foreach(ModuleRootModificationUtil.setModuleSdk(_, sdk))

    IndexingTestUtil.waitUntilIndexesAreReady(getProject)

    module1 = modules.find(_.getName == "module1").orNull
    assertNotNull("Could not find module with name 'module1'", module1)
    module2 = modules.find(_.getName == "module2").orNull
    assertNotNull("Could not find module with name 'module2'", module2)
    compiler = new CompilerTester(getProject, java.util.Arrays.asList(modules: _*), null, false)
  }

  override def tearDown(): Unit = try {
    compiler.tearDown()
    val settings = ScalaCompileServerSettings.getInstance()
    settings.USE_DEFAULT_SDK = true
    settings.COMPILE_SERVER_SDK = null
    inWriteAction {
      val jdkTable = ProjectJdkTable.getInstance()
      jdkTable.removeJdk(sdk)
      val kotlinSdk = jdkTable.getAllJdks.find(_.getName.contains("Kotlin SDK"))
      kotlinSdk.foreach(jdkTable.removeJdk)
    }
  } finally {
    super.tearDown()
  }

  def testPolyglotCompilation(): Unit = {
    assertEquals(IncrementalityType.SBT, ScalaCompilerConfiguration.instanceIn(getProject).incrementalityType)
    compiler.make()
    assertClassExists("Greeter", module1)
    assertClassExists("AbstractGreeter", module1)
    assertClassExists("HelloWorldGreeter", module2)
  }

  private def assertClassExists(name: String, module: Module): Unit = {
    val file = compiler.findClassFile(name, module)
    assertNotNull(s"Could not find class file for $name", file)
  }
}
