package org.jetbrains.plugins.scala.compiler.buildtools

import com.intellij.maven.testFramework.MavenImportingTestCase
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.{CompilerTester, EdtTestUtil}
import org.jetbrains.plugins.scala.base.libraryLoaders.SmartJDKLoader
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.compiler.JdkVersionParameters
import org.jetbrains.plugins.scala.compiler.testUtils.CompileServerTestUtil
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.util.runners.TestJdkVersion
import org.jetbrains.plugins.scala.{CompilationTests_IDEA, CompilationTests_Zinc}
import org.junit.Assert.{assertNotNull, assertTrue}
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

@RunWith(classOf[Parameterized])
class MavenProjectWithPureJavaModuleTest(jdkVersion: TestJdkVersion) extends MavenImportingTestCase {

  private var sdk: Sdk = uninitialized

  override protected def runInDispatchThread(): Boolean = false

  override def setUp(): Unit = {
    super.setUp()

    EdtTestUtil.runInEdtAndWait { () =>
      val res = SmartJDKLoader.getOrCreateJDK(jdkVersion.toProductionVersion)
      val settings = ScalaCompileServerSettings.getInstance()
      settings.COMPILE_SERVER_SDK = res.getName
      settings.USE_DEFAULT_SDK = false
      sdk = res
    }

    CompileServerTestUtil.registerLongRunningThreads()

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
        |""".stripMargin,
      false,
    )
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
        |    </build>""".stripMargin,
      false,
    )
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
        |    </dependencies>""".stripMargin,
      false,
    )
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
    EdtTestUtil.runInEdtAndWait { () =>
      val settings = ScalaCompileServerSettings.getInstance()
      settings.USE_DEFAULT_SDK = true
      settings.COMPILE_SERVER_SDK = null
      inWriteAction(ProjectJdkTable.getInstance().removeJdk(sdk))
    }
  } finally {
    super.tearDown()
  }

  @Category(Array(classOf[CompilationTests_Zinc]))
  @Test
  def importAndCompile_Zinc(): Unit =
    runImportAndCompileTest(IncrementalityType.SBT)

  @Category(Array(classOf[CompilationTests_IDEA]))
  @Test
  def importAndCompile_IDEA(): Unit =
    runImportAndCompileTest(IncrementalityType.IDEA)

  private def runImportAndCompileTest(incrementality: IncrementalityType): Unit = {
    importProject()

    ScalaCompilerConfiguration.instanceIn(getProject).incrementalityType = incrementality

    val modules = ModuleManager.getInstance(getProject).getModules
    modules.foreach(ModuleRootModificationUtil.setModuleSdk(_, sdk))

    withCompiler { compiler =>
      val jdk21warnings = Set(
        "source value 8 is obsolete and will be removed in a future release",
        "target value 8 is obsolete and will be removed in a future release",
        "To suppress warnings about obsolete options, use -Xlint:-options"
      )

      val bootstrapClasspathWarnings = incrementality match {
        case IncrementalityType.SBT => Set("bootstrap class path not set in conjunction with -source 8")
        case IncrementalityType.IDEA => Set.empty
      }

      val messages = compiler.make()
      val errorsAndWarnings = messages.asScala.filter { message =>
        val category = message.getCategory
        category == CompilerMessageCategory.ERROR || category == CompilerMessageCategory.WARNING
      }.filterNot(msg => jdk21warnings.exists(s => msg.getMessage.contains(s)))
        .filterNot(msg => bootstrapClasspathWarnings.exists(s => msg.getMessage.contains(s)))

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

  private def withCompiler(action: CompilerTester => Unit): Unit = {
    val project = getProject
    val modules = ModuleManager.getInstance(project).getModules
    val compiler = new CompilerTester(project, java.util.Arrays.asList(modules*), null, false)
    try action(compiler)
    finally compiler.tearDown()
  }
}

private object MavenProjectWithPureJavaModuleTest extends JdkVersionParameters
