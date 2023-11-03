package org.jetbrains.plugins.scala.compiler.buildtools

import com.intellij.maven.testFramework.MavenImportingTestCase
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.module.{ModuleManager, ModuleTypeManager, StdModuleTypes}
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.CompilerTester
import org.jetbrains.plugins.scala.CompilationTests
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.util.runners.TestJdkVersion
import org.junit.Assert.{assertNotNull, assertTrue}
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters._

@Category(Array(classOf[CompilationTests]))
abstract class MavenProjectWithPureJavaModuleTestBase(incrementality: IncrementalityType) extends MavenImportingTestCase {

  private var sdk: Sdk = _

  private var compiler: CompilerTester = _

  override def setUp(): Unit = {
    super.setUp()

    // Without this HACK for some reason different instances of com.intellij.openapi.module.JavaModuleType will be used
    // in org.jetbrains.idea.maven.importing.MavenImporter (e.g. ScalaMavenImporter)
    // and org.jetbrains.idea.maven.importing.MavenModuleImporter
    // (Note that it uses `==` instead of `equals` for some reason: `importer.getModuleType() == moduleType`)
    //noinspection ApiStatus
    ModuleTypeManager.getInstance.registerModuleType(StdModuleTypes.JAVA)

    sdk = {
      val jdkVersion =
        Option(System.getProperty("filter.test.jdk.version"))
          .map(TestJdkVersion.valueOf)
          .getOrElse(TestJdkVersion.JDK_17)
          .toProductionVersion

      val res = SmartJDKLoader.getOrCreateJDK(jdkVersion)
      val settings = ScalaCompileServerSettings.getInstance()
      settings.COMPILE_SERVER_SDK = res.getName
      settings.USE_DEFAULT_SDK = false
      res
    }

    createProjectSubDirs("module1/src/main/java", "module2/src/main/scala")
    createProjectPom(
      """    <groupId>org.example</groupId>
        |    <artifactId>pure-java</artifactId>
        |    <packaging>pom</packaging>
        |    <version>1.0-SNAPSHOT</version>
        |
        |    <modules>
        |        <module>module1</module>
        |        <module>module2</module>
        |    </modules>
        |""".stripMargin)
    createModulePom("module1",
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
        |    </build>""".stripMargin)
    createModulePom("module2",
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
        |    </dependencies>""".stripMargin)
    createProjectSubFile("module1/src/main/java/Greeter.java",
      """interface Greeter {
        |  String greeting();
        |}
        |""".stripMargin)
    createProjectSubFile("module2/src/main/scala/HelloWorldGreeter.scala",
      """object HelloWorldGreeter extends Greeter {
        |  def greeting: String = "Hello, world!"
        |}
        |""".stripMargin)
  }

  override def tearDown(): Unit = try {
    CompileServerLauncher.stopServerAndWait()
    compiler.tearDown()
    val settings = ScalaCompileServerSettings.getInstance()
    settings.USE_DEFAULT_SDK = true
    settings.COMPILE_SERVER_SDK = null
    inWriteAction(ProjectJdkTable.getInstance().removeJdk(sdk))
    //noinspection ApiStatus
    ModuleTypeManager.getInstance.unregisterModuleType(StdModuleTypes.JAVA)
  } finally {
    super.tearDown()
  }

  def testImportAndCompile(): Unit = {
    importProject()

    ScalaCompilerConfiguration.instanceIn(myProject).incrementalityType = incrementality

    val modules = ModuleManager.getInstance(myProject).getModules
    modules.foreach(ModuleRootModificationUtil.setModuleSdk(_, sdk))
    compiler = new CompilerTester(myProject, java.util.Arrays.asList(modules: _*), null, false)

    val messages = compiler.make()
    val errorsAndWarnings = messages.asScala.filter { message =>
      val category = message.getCategory
      category == CompilerMessageCategory.ERROR || category == CompilerMessageCategory.WARNING
    }

    assertTrue(
      s"Expected no compilation errors or warnings, got: ${errorsAndWarnings.mkString(System.lineSeparator())}",
      errorsAndWarnings.isEmpty
    )

    val module1 = modules.find(_.getName == "module1").orNull
    assertNotNull("Could not find module with name 'module1'", module1)
    val module2 = modules.find(_.getName == "module2").orNull
    assertNotNull("Could not find module with name 'module2'", module2)

    val greeter = compiler.findClassFile("Greeter", module1)
    assertNotNull("Could not find compiled class file Greeter", greeter)

    val helloWorldGreeter = compiler.findClassFile("HelloWorldGreeter", module2)
    assertNotNull("Could not find compiled class file HelloWorldGreeter", helloWorldGreeter)

    val helloWorldGreeterModule = compiler.findClassFile("HelloWorldGreeter$", module2)
    assertNotNull("Could not find compiled class file HelloWorldGreeter$", helloWorldGreeterModule)
  }
}

class MavenProjectWithPureJavaModuleTest_IDEA extends MavenProjectWithPureJavaModuleTestBase(IncrementalityType.IDEA)

class MavenProjectWithPureJavaModuleTest_Zinc extends MavenProjectWithPureJavaModuleTestBase(IncrementalityType.SBT)
