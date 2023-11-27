package org.jetbrains.plugins.scala.compiler.buildtools

import com.intellij.maven.testFramework.MavenImportingTestCase
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
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
import org.junit.Assert.{assertEquals, assertNotNull, assertTrue}
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters._

@Category(Array(classOf[CompilationTests]))
class ConfigureIncrementalCompilerMavenTest extends MavenImportingTestCase {

  private var sdk: Sdk = _

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

      SmartJDKLoader.getOrCreateJDK(jdkVersion)
    }

    createProjectSubDirs("module1/src/main/java", "module2/src/main/scala", "module3/src/main/kotlin")
    createProjectPom(
      """    <groupId>org.example</groupId>
        |    <artifactId>pure-java</artifactId>
        |    <packaging>pom</packaging>
        |    <version>1.0-SNAPSHOT</version>
        |
        |    <modules>
        |        <module>module1</module>
        |        <module>module2</module>
        |        <module>module3</module>
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
        |    </build>""".stripMargin)
    createModulePom("module3",
      """<!-- parent pom -->
        |    <parent>
        |        <groupId>org.example</groupId>
        |        <artifactId>pure-java</artifactId>
        |        <version>1.0-SNAPSHOT</version>
        |    </parent>
        |
        |    <artifactId>module3</artifactId>
        |    <version>1.0-SNAPSHOT</version>
        |    <packaging>jar</packaging>
        |
        |    <properties>
        |        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        |        <kotlin.code.style>official</kotlin.code.style>
        |        <kotlin.compiler.jvmTarget>1.8</kotlin.compiler.jvmTarget>
        |    </properties>
        |
        |    <repositories>
        |        <repository>
        |            <id>mavenCentral</id>
        |            <url>https://repo1.maven.org/maven2/</url>
        |        </repository>
        |    </repositories>
        |
        |    <build>
        |        <sourceDirectory>src/main/kotlin</sourceDirectory>
        |        <testSourceDirectory>src/test/kotlin</testSourceDirectory>
        |        <plugins>
        |            <plugin>
        |                <groupId>org.jetbrains.kotlin</groupId>
        |                <artifactId>kotlin-maven-plugin</artifactId>
        |                <version>1.9.0</version>
        |                <executions>
        |                    <execution>
        |                        <id>compile</id>
        |                        <phase>compile</phase>
        |                        <goals>
        |                            <goal>compile</goal>
        |                        </goals>
        |                    </execution>
        |                    <execution>
        |                        <id>test-compile</id>
        |                        <phase>test-compile</phase>
        |                        <goals>
        |                            <goal>test-compile</goal>
        |                        </goals>
        |                    </execution>
        |                </executions>
        |            </plugin>
        |        </plugins>
        |    </build>""".stripMargin)
    createProjectSubFile("module1/src/main/java/Foo.java", "class Foo {}".stripMargin)
    createProjectSubFile("module2/src/main/scala/Bar.scala", "class Bar".stripMargin)
    createProjectSubFile("module3/src/main/kotlin/Baz.kt", "class Baz {}".stripMargin)
  }

  override def tearDown(): Unit = try {
    inWriteAction {
      val jdkTable = ProjectJdkTable.getInstance()
      jdkTable.removeJdk(sdk)
      val kotlinSdk = jdkTable.getAllJdks.find(_.getName.contains("Kotlin SDK"))
      kotlinSdk.foreach(jdkTable.removeJdk)
    }
    //noinspection ApiStatus
    ModuleTypeManager.getInstance.unregisterModuleType(StdModuleTypes.JAVA)
  } finally {
    super.tearDown()
  }

  def testIncrementalCompilerSetUp(): Unit = {
    importProject()

    val modules = ModuleManager.getInstance(myProject).getModules
    modules.foreach(ModuleRootModificationUtil.setModuleSdk(_, sdk))

    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    assertEquals(IncrementalityType.IDEA, ScalaCompilerConfiguration.instanceIn(myProject).incrementalityType)
  }
}
