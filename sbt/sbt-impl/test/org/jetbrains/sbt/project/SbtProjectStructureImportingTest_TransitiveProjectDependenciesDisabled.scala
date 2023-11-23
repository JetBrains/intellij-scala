package org.jetbrains.sbt.project

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfiguration
import com.intellij.openapi.externalSystem.util.{DisposeAwareProjectChange, ExternalSystemApiUtil}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{ProjectJdkTable, Sdk}
import com.intellij.openapi.roots.{LanguageLevelModuleExtension, LanguageLevelProjectExtension, ModuleRootModificationUtil}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiManager
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.annotations.Nullable
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions
import org.jetbrains.jps.model.java.{JavaResourceRootType, JavaSourceRootType}
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.compiler.data.CompileOrder
import org.jetbrains.plugins.scala.extensions.{RichFile, inWriteAction}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.project.external.JdkByName
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.jetbrains.sbt.actions.SbtDirectoryCompletionContributor
import org.jetbrains.sbt.settings.SbtSettings
import org.junit.Assert
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.experimental.categories.Category

import java.net.URI
import scala.annotation.nowarn
import scala.jdk.CollectionConverters.{CollectionHasAsScala, SeqHasAsJava}

// IMPORTANT ! each test that tests the dependencies of the modules should have its counterpart in
// SbtProjectStructureImportingTest_TransitiveProjectDependenciesEnabled.scala. Before each test performed in this class
// insertProjectTransitiveDependencies is set to true, so that the functionality of transitive dependencies can be tested
@Category(Array(classOf[SlowTests]))
final class SbtProjectStructureImportingTest_TransitiveProjectDependenciesDisabled extends SbtProjectStructureImportingLike {

  import ProjectStructureDsl._

  // note: it is needed to set insertProjectTransitiveDependencies to false in projectSettings because it is enabled
  //by default
  override def setUp(): Unit = {
    super.setUp()
    val projectSettings = getCurrentExternalProjectSettings
    projectSettings.setInsertProjectTransitiveDependencies(false)
  }

  def testSimple(): Unit = {
    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdk("2.13.5")
    runSimpleTest("simple", scalaLibraries)

    // Adding the assertion here not to create a separate heavy test for such a tiny check
    // org.jetbrains.plugins.scala.project.ProjectExt#modulesWithScala
    Assert.assertEquals(
      "modulesWithScala should return list of non *-build modules",
      Seq("simple"),
      myProject.modulesWithScala.map(_.getName),
    )

    val expectedLineInProcessOutput = "[error] Some error message which shouldn't fail the whole build, see SCL-21478 and SCL-13038"
    assertTrue(
      s"Can't find this line in sbt process output during sbt structure extraction:\n$expectedLineInProcessOutput",
      SbtProjectResolver.processOutputOfLatestStructureDump.contains(expectedLineInProcessOutput)
    )
  }

  //noinspection RedundantDefaultArgument
  def testSimple_Scala3(): Unit = {
    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibrary("2.13.6") +: ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdk("3.0.2")
    runSimpleTest("simple-scala3", scalaLibraries, ExpectedDirectoryCompletionVariant.DefaultSbtContentRootsScala3)
  }

  def testSimpleDoNotUseCoursier(): Unit = {
    val scalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkFromIvy("2.12.10")
    runSimpleTest("simpleDoNotUseCoursier", scalaLibraries, ExpectedDirectoryCompletionVariant.DefaultSbtContentRootsScala212)
  }

  private def runSimpleTest(
    projectName: String,
    expectedScalaLibraries: Seq[library],
    expectedSbtCompletionVariants: Seq[ExpectedDirectoryCompletionVariant] = ExpectedDirectoryCompletionVariant.DefaultSbtContentRootsScala213
  ): Unit = {
    runTest(
      new project(projectName) {
        libraries := expectedScalaLibraries

        modules := Seq(
          new module(projectName) {
            contentRoots += getProjectPath
            ProjectStructureDsl.sources := Seq("src/main/scala", "src/main/java")
            testSources := Seq("src/test/scala", "src/test/java")
            resources := Seq("src/main/resources")
            testResources := Seq("src/test/resources")
            excluded := Seq("target")
            libraryDependencies := expectedScalaLibraries
          },
          new module(s"$projectName-build") {
            ProjectStructureDsl.sources := Seq("")
            excluded := Seq("project/target", "target")
          }
        )
      }
    )

    val projectBaseDir = myProject.baseDir
    assertSbtDirectoryCompletionContributorVariants(
      projectBaseDir,
      expectedSbtCompletionVariants
    )
  }

  //NOTE: it doesn't test final ordering on UI, see IDEA-306694
  private def assertSbtDirectoryCompletionContributorVariants(
    directory: VirtualFile,
    expectedVariants: Seq[ExpectedDirectoryCompletionVariant]
  ): Unit = {
    val psiDirectory = PsiManager.getInstance(myProject).findDirectory(directory)
    val directoryPath = directory.getPath

    val variants = new SbtDirectoryCompletionContributor().getVariants(psiDirectory).asScala.toSeq
    val actualVariants = variants.map(v => ExpectedDirectoryCompletionVariant(
      v.getPath.stripPrefix(directoryPath).stripPrefix("/"),
      v.getRootType
    ))

    assertCollectionEquals(
      "Wrong directory completion contributor variants",
      expectedVariants,
      actualVariants
    )
  }

  private case class ExpectedDirectoryCompletionVariant(
    projectRelativePath: String,
    rootType: JpsModuleSourceRootType[_]
  )

  private object ExpectedDirectoryCompletionVariant {
    val DefaultSbtContentRootsScala212: Seq[ExpectedDirectoryCompletionVariant] = Seq(
      ("src/main/java", JavaSourceRootType.SOURCE),
      ("src/main/scala", JavaSourceRootType.SOURCE),
      ("src/main/scala-2", JavaSourceRootType.SOURCE),
      ("src/main/scala-2.12", JavaSourceRootType.SOURCE),
      ("src/test/java", JavaSourceRootType.TEST_SOURCE),
      ("src/test/scala", JavaSourceRootType.TEST_SOURCE),
      ("src/test/scala-2", JavaSourceRootType.TEST_SOURCE),
      ("src/test/scala-2.12", JavaSourceRootType.TEST_SOURCE),
      ("src/main/resources", JavaResourceRootType.RESOURCE),
      ("src/test/resources", JavaResourceRootType.TEST_RESOURCE),
    ).map((ExpectedDirectoryCompletionVariant.apply _).tupled)

    val DefaultSbtContentRootsScala213: Seq[ExpectedDirectoryCompletionVariant] = Seq(
      ("src/main/java", JavaSourceRootType.SOURCE),
      ("src/main/scala", JavaSourceRootType.SOURCE),
      ("src/main/scala-2", JavaSourceRootType.SOURCE),
      ("src/main/scala-2.13", JavaSourceRootType.SOURCE),
      ("src/test/java", JavaSourceRootType.TEST_SOURCE),
      ("src/test/scala", JavaSourceRootType.TEST_SOURCE),
      ("src/test/scala-2", JavaSourceRootType.TEST_SOURCE),
      ("src/test/scala-2.13", JavaSourceRootType.TEST_SOURCE),
      ("src/main/resources", JavaResourceRootType.RESOURCE),
      ("src/test/resources", JavaResourceRootType.TEST_RESOURCE),
    ).map((ExpectedDirectoryCompletionVariant.apply _).tupled)

    val DefaultSbtContentRootsScala3: Seq[ExpectedDirectoryCompletionVariant] = Seq(
      ("src/main/java", JavaSourceRootType.SOURCE),
      ("src/main/scala", JavaSourceRootType.SOURCE),
      ("src/main/scala-3", JavaSourceRootType.SOURCE),
      ("src/test/java", JavaSourceRootType.TEST_SOURCE),
      ("src/test/scala", JavaSourceRootType.TEST_SOURCE),
      ("src/test/scala-3", JavaSourceRootType.TEST_SOURCE),
      ("src/main/resources", JavaResourceRootType.RESOURCE),
      ("src/test/resources", JavaResourceRootType.TEST_RESOURCE),
    ).map((ExpectedDirectoryCompletionVariant.apply _).tupled)
  }

  def testProjectWithUppercaseName(): Unit = runTest {
    new project("MyProjectWithUppercaseName") {
      lazy val scalaLibraries: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdk("2.13.6")
      libraries ++= scalaLibraries

      modules := Seq(
        new module("MyProjectWithUppercaseName") {
          libraryDependencies ++= scalaLibraries
        },
        new module("MyProjectWithUppercaseName-build") {
        }
      )
    }
  }

  def testMultiModule(): Unit = runTest(
    new project("multiModule") {
      lazy val foo: module = new module("foo") {
        moduleDependencies += new dependency(bar) {
          isExported := true
        }
      }

      lazy val bar  = new module("bar")
      lazy val root = new module("multiModule")

      modules := Seq(root, foo, bar)
    })

  def testUnmanagedDependency(): Unit = runTest(
    new project("unmanagedDependency") {
      val scalaLibraries: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdk("2.13.6")
      libraries := scalaLibraries

      modules += new module("unmanagedDependency") {
        lazy val unmanagedLibrary: library = new library("sbt: unmanaged-jars") {
          libClasses += (getTestProjectDir / "lib" / "unmanaged.jar").getAbsolutePath
        }

        libraries := Seq(unmanagedLibrary)
        val myLibraryDependencies: Seq[library] = unmanagedLibrary +: scalaLibraries
        libraryDependencies := myLibraryDependencies
      }
    }
  )

  def testSharedSources(): Unit = runTest(
    new project("sharedSourcesProject") {
      lazy val scalaLibraries: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdk("2.13.6")
      libraries := scalaLibraries

      lazy val root: module = new module("sharedSourcesProject") {
        contentRoots := Seq(getProjectPath)
        sources := Seq("src/main/scala")
        libraryDependencies := scalaLibraries
        moduleDependencies := Nil
      }

      lazy val sharedSourcesModule: module = new module("sharedSources-sources") {
        contentRoots := Seq(getProjectPath + "/shared")
        libraryDependencies := scalaLibraries
        sources := Seq("src/main/scala")
      }

      lazy val foo: module = new module("foo") {
        libraryDependencies := scalaLibraries
        moduleDependencies := Seq(
          new dependency(sharedSourcesModule) { isExported := true }
        )
      }

      lazy val bar: module = new module("bar") {
        libraryDependencies := scalaLibraries
        moduleDependencies := Seq(
          new dependency(sharedSourcesModule) { isExported := true }
        )
      }

      modules := Seq(root, foo, bar, sharedSourcesModule)
    }
  )

  def testSbtIdeSettingsRespectIdeExcludedDirectoriesSetting(): Unit = runTest(
    new project("root") {
      modules += new module("root") {
        excluded := Seq(
          "directory-to-exclude-1",
          "directory/to/exclude/2"
        )
      }
    }
  )

  /**
   * SCL-12520: Generate shared sources module when it is only used form a single other module
   */
  def testSCL12520(): Unit = runTest(
    new project("scl12520") {
      val sharedModule: module = new module("p1-sources") {
        contentRoots += getProjectPath + "/p1/shared"
      }

      val jvmModule: module = new module("p1") {
        moduleDependencies += new dependency(sharedModule) { isExported := true }
        contentRoots += getProjectPath + "/p1/jvm"
      }

      val rootModule: module = new module("scl12520") {}
      val rootBuildModule: module = new module("scl12520-build") {}

      modules := Seq(sharedModule, rootModule, rootBuildModule, jvmModule)
    }
  )

  /**
   * SCL-13600: generate all modules when there is a duplicate project id in the sbt build
   * due to references to different builds, or multiple sbt projects being imported independently from IDEA
   */
  def testSCL13600(): Unit = runTest(
    new project("root") {
      val buildURI: URI = getTestProjectDir.getCanonicalFile.toURI

      val rootC1: module = new module("Build C1 Name", Array("Build C1 Name")) {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("c1/")
        moduleDependencies := Seq()
      }
      val rootC2: module = new module("Build C2 Name", Array("Build C2 Name")) {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("c2/")
        moduleDependencies := Seq()
      }
      val rootC3: module = new module("suffix2.root", Array("root1")) {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("prefix1/prefix2/c3/suffix1/suffix2/")
        moduleDependencies := Seq()
      }
      val rootC4: module = new module("suffix1.suffix2.root", Array("root2")) {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("prefix1/prefix2/c4/suffix1/suffix2/")
        moduleDependencies := Seq()
      }
      val root: module = new module("root") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI
        moduleDependencies := Seq(
          new dependency(rootC1) {isExported := true },
          new dependency(rootC2) {isExported := true },
          new dependency(rootC3) {isExported := true },
          new dependency(rootC4) {isExported := true },
        )
      }

      val modulesFromRoot: Seq[module] = Seq(
        new module("project1InRootBuild"),
        new module("project2InRootBuild"),
        new module("project3InRootBuildWithSameName", Array("same name in root build")),
        new module("project4InRootBuildWithSameName", Array("same name in root build")),
        new module("project5InRootBuildWithSameGlobalName", Array("same global name")),
        new module("project6InRootBuildWithSameGlobalName", Array("same global name")),
      )
      val modulesFromC1: Seq[module] = Seq(
        rootC1,
        new module("project1InC1", Array("Build C1 Name")),
        new module("project2InC1", Array("Build C1 Name")),
        new module("project3InC1WithSameName", Array("Build C1 Name", "same name in c1")),
        new module("project4InC1WithSameName", Array("Build C1 Name", "same name in c1")),
        new module("project5InC1WithSameGlobalName", Array("Build C1 Name", "same global name")),
        new module("project6InC1WithSameGlobalName", Array("Build C1 Name", "same global name")),
      )
      val modulesFromC2: Seq[module] = Seq(
        rootC2,
        new module("project1InC2", Array("Build C2 Name")),
        new module("project2InC2", Array("Build C2 Name")),
        new module("project3InC2WithSameName", Array("Build C2 Name", "same name in c2")),
        new module("project4InC2WithSameName", Array("Build C2 Name", "same name in c2")),
        new module("project5InC2WithSameGlobalName", Array("Build C2 Name", "same global name")),
        new module("project6InC2WithSameGlobalName", Array("Build C2 Name", "same global name")),
      )
      val modulesFromC3: Seq[module] = Seq(
        rootC3,
        new module("project1InC3", Array("root1")),
        new module("project2InC3", Array("root1")),
        new module("project3InC3WithSameName", Array("root1", "same name in c3")),
        new module("project4InC3WithSameName", Array("root1", "same name in c3")),
        new module("project5InC3WithSameGlobalName", Array("root1", "same global name")),
        new module("project6InC3WithSameGlobalName", Array("root1", "same global name")),
      )
      val modulesFromC4: Seq[module] = Seq(
        rootC4,
        new module("project1InC4", Array("root2")),
        new module("project2InC4", Array("root2")),
        new module("project3InC4WithSameName", Array("root2", "same name in c4")),
        new module("project4InC4WithSameName", Array("root2", "same name in c4")),
        new module("project5InC4WithSameGlobalName", Array("root2", "same global name")),
        new module("project6InC4WithSameGlobalName", Array("root2", "same global name")),
      )

      modules := root +:
        modulesFromRoot ++:
        modulesFromC1 ++:
        modulesFromC2 ++:
        modulesFromC3 ++:
        modulesFromC4
    }
  )

  def testSCL14635(): Unit = runTest(
    new project("SCL-14635") {
      private val buildURI: URI = getTestProjectDir.getCanonicalFile.toURI

      private val sbtIdeaPluginGroup = Array("sbtIdeaPlugin")
      private val sbtIdeaShellGroup = Array("sbt-idea-shell")
      private val sbtIdeSettingsGroup = Array("sbt-ide-settings")

      modules := Seq(
        new module("SCL-14635") {
          sbtBuildURI := buildURI
          sbtProjectId := "root"
        },
        new module("SCL-14635-build"),

        // NOTE: sbtIdeaPlugin also has inner module named `sbt-idea-plugin` (with dashes), but it's separate, non-root module
        new module("sbtIdeaPlugin", sbtIdeaPluginGroup) {
          sbtBuildURI := new URI("https://github.com/JetBrains/sbt-idea-plugin.git")
          sbtProjectId := "sbtIdeaPlugin"
        },
        new module("sbt-idea-plugin", sbtIdeaPluginGroup),
        new module("sbt-declarative-core", sbtIdeaPluginGroup),
        new module("sbt-declarative-packaging", sbtIdeaPluginGroup),
        new module("sbt-declarative-visualizer", sbtIdeaPluginGroup),
        new module("sbtIdeaPlugin-build", sbtIdeaPluginGroup),

        new module("sbt-idea-shell", sbtIdeaShellGroup) {
          sbtBuildURI := new URI("https://github.com/JetBrains/sbt-idea-shell.git#master")
          sbtProjectId := "root"
        },
        new module("sbt-idea-shell-build", sbtIdeaShellGroup),

        new module("sbt-ide-settings", sbtIdeSettingsGroup) {
          sbtBuildURI := new URI("https://github.com/JetBrains/sbt-ide-settings.git")
          sbtProjectId := "sbt-ide-settings"
        },
        new module("sbt-ide-settings-build", sbtIdeSettingsGroup)
      )
    }
  )

  def testCrossplatform(): Unit = runTest(
    new project("crossplatform") {
      lazy val root = new module("crossplatform")
      lazy val crossJS = new module("crossJS", Array("cross"))
      lazy val crossJVM = new module("crossJVM", Array("cross"))
      lazy val crossNative = new module("crossNative", Array("cross"))
      lazy val crossSources = new module("cross-sources", Array("cross"))
      lazy val jsJvmSources = new module("js-jvm-sources", Array("cross"))
      lazy val jsNativeSources = new module("js-native-sources", Array("cross"))
      lazy val jvmNativeSources = new module("jvm-native-sources", Array("cross"))

      modules := Seq(root, crossJS, crossJVM, crossNative, crossSources, jsJvmSources, jsNativeSources, jvmNativeSources)
    }
  )

  //noinspection TypeAnnotation
  // SCL-16204, SCL-17597
  def testJavaLanguageLevelAndTargetByteCodeLevel(): Unit = {
    //overriding project jdk (configured in base test class)
    val projectSdk9 = IdeaTestUtil.getMockJdk9
    inWriteAction {
      ProjectJdkTable.getInstance.addJdk(projectSdk9)
    }
    getCurrentExternalProjectSettings.jdk = projectSdk9.getName

    //sbt can't be run with mock project JDK, so ensure it has normal SDK (configured in base test class)
    setSbtSettingsCustomSdk(getJdkConfiguredForTestCase)

    try runTest(
      new project("java-language-level-and-target-byte-code-level") {
        // we expect no other options except -source -target --release or --enable-preview in this test
        // these options are specially handled and saved in the dedicated settings, so we don't expect any extra javacOptions
        javacOptions := Nil
        sdk := JdkByName(projectSdk9.getName)

        def moduleX(name: String, source: LanguageLevel, @Nullable target: String): module = new module(name) {
          javaLanguageLevel := source
          javaTargetBytecodeLevel := target
          javacOptions := Nil
          sdk := JdkByName(projectSdk9.getName)
        }

        val sdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_1_9

        val root = moduleX("java-language-level-and-target-byte-code-level", sdkLanguageLevel, null)

        // Module naming: `source_target_release`
        // `x` means option is missing
        val module_x_x_x = moduleX("module_x_x_x", sdkLanguageLevel, null)

        val module_8_8_x   = moduleX("module_8_8_x", LanguageLevel.JDK_1_8, "8")
        val module_8_11_x  = moduleX("module_8_11_x", LanguageLevel.JDK_1_8, "11")
        val module_11_8_x  = moduleX("module_11_8_x", LanguageLevel.JDK_11, "8")
        val module_11_11_x = moduleX("module_11_11_x", LanguageLevel.JDK_11, "11")

        // no explicit target: javac will use source level by default
        val module_8_x_x  = moduleX("module_8_x_x", LanguageLevel.JDK_1_8, null)
        val module_11_x_x = moduleX("module_11_x_x", LanguageLevel.JDK_11, null)
        val module_14_x_x = moduleX("module_14_x_x", LanguageLevel.JDK_14, null)
        val module_15_x_x = moduleX("module_15_x_x", LanguageLevel.JDK_15, null)

        val module_x_8_x  = moduleX("module_x_8_x", sdkLanguageLevel, "8")
        val module_x_11_x = moduleX("module_x_11_x", sdkLanguageLevel, "11")

        val module_x_x_8  = moduleX("module_x_x_8", LanguageLevel.JDK_1_8, "8")
        val module_x_x_11 = moduleX("module_x_x_11", LanguageLevel.JDK_11, "11")

        // Java preview features
        // NOTE: IntelliJ API supports only 2 last preview versions of java language level (in com.intellij.pom.java.LanguageLevel)
        // When a new version of Java releases and IDEA supports it, we should update this test
        //
        // no explicit target: javac will use source level by default
        val module_8_x_x_preview  = moduleX("module_8_x_x_preview", LanguageLevel.JDK_1_8, null) // no preview for Java 8
        val module_11_x_x_preview = moduleX("module_11_x_x_preview", LanguageLevel.JDK_11, null) // no preview for Java 11
        val module_14_x_x_preview = moduleX("module_14_x_x_preview", LanguageLevel.JDK_14, null) // no preview for Java 11
        val module_20_x_x_preview = moduleX("module_20_x_x_preview", LanguageLevel.JDK_20_PREVIEW, null)

        val module_x_x_8_preview  = moduleX("module_x_x_8_preview", LanguageLevel.JDK_1_8, "8")
        val module_x_x_11_preview = moduleX("module_x_x_11_preview", LanguageLevel.JDK_11, "11")
        val module_x_x_14_preview = moduleX("module_x_x_14_preview", LanguageLevel.JDK_14, "14")
        val module_x_x_20_preview = moduleX("module_x_x_20_preview", LanguageLevel.JDK_20_PREVIEW, "20")

        modules := Seq(
          root,
          module_x_x_x,
          module_8_8_x, module_8_11_x, module_11_8_x, module_11_11_x,
          module_8_x_x, module_11_x_x, module_14_x_x, module_15_x_x,
          module_x_8_x, module_x_11_x,
          module_x_x_8, module_x_x_11,
          module_8_x_x_preview, module_11_x_x_preview, module_14_x_x_preview, module_20_x_x_preview,
          module_x_x_8_preview, module_x_x_11_preview, module_x_x_14_preview, module_x_x_20_preview,
        )
      }
    ) finally {
      inWriteAction {
        ProjectJdkTable.getInstance.removeJdk(projectSdk9)
      }
    }
  }

  private def setSbtSettingsCustomSdk(sdk: Sdk): Unit = {
    val settings = SbtSettings.getInstance(myProject)
    settings.setCustomVMPath(sdk.getHomePath)
    settings.setCustomVMEnabled(true)
  }

  //noinspection TypeAnnotation
  // SCL-16204, SCL-17597
  @nowarn("cat=deprecation")
  def testJavaLanguageLevelAndTargetByteCodeLevel_NoOptions(): Unit = {
    val projectLangaugeLevel = SbtProjectStructureImportingTest_TransitiveProjectDependenciesDisabled.this.projectJdkLanguageLevel

    def doRunTest(): Unit = runTest(
      new project("java-language-level-and-target-byte-code-level-no-options") {
        javacOptions := Nil
        javaLanguageLevel := projectLangaugeLevel
        javaTargetBytecodeLevel := null

        def moduleX(name: String, source: LanguageLevel, @Nullable target: String): module = new module(name) {
          javaLanguageLevel := source
          javaTargetBytecodeLevel := target
          javacOptions := Nil
        }

        val root = moduleX("java-language-level-and-target-byte-code-level-no-options", projectLangaugeLevel, null)
        val module1 = moduleX("module1", projectLangaugeLevel, null)

        modules := Seq(root, module1)
      }
    )

    doRunTest()

    // Emulate User changing the settings manually
    ExternalSystemApiUtil.executeProjectChangeAction(new DisposeAwareProjectChange(myProject) {
      override def execute(): Unit = {
        val ManuallySetTarget = "9"
        val ManuallySetSource = LanguageLevel.JDK_1_9

        setOptions(myProject, ManuallySetSource, ManuallySetTarget, Seq("-some-root-option"))

        val projectModules = myProject.modules
        projectModules.foreach(setOptions(_, ManuallySetSource, ManuallySetTarget, Seq("-some-module-option")))
      }
    })

    // Manually set settings should be rewritten if no explicit javac options provided
    doRunTest()
  }

  private def setOptions(project: Project, source: LanguageLevel, target: String, other: Seq[String]): Unit = {
    val compilerSettings = CompilerConfiguration.getInstance(project)
    compilerSettings.setProjectBytecodeTarget(target)

    val options = JavacConfiguration.getOptions(project, classOf[JavacConfiguration])
    options.ADDITIONAL_OPTIONS_STRING = other.mkString(" ")

    val ext = LanguageLevelProjectExtension.getInstance(project)
    ext.setLanguageLevel(source)
  }

  private def setOptions(module: Module, source: LanguageLevel, target: String, other: Seq[String]): Unit = {
    val compilerSettings = CompilerConfiguration.getInstance(module.getProject)
    compilerSettings.setBytecodeTargetLevel(module, target)
    compilerSettings.setAdditionalOptions(module, other.asJava)

    ModuleRootModificationUtil.updateModel(module,
      _.getModuleExtension(classOf[LanguageLevelModuleExtension]).setLanguageLevel(source)
    )
  }

  // TODO: also create test for scalacOptions

  //noinspection TypeAnnotation
  def testJavacOptionsPerModule(): Unit = runTest(
    new project("javac-options-per-module") {
      javacOptions := Nil // no storing project level options

      def moduleX(name: String, expectedJavacOptions: Seq[String]): module = new module(name) {
        javacOptions := expectedJavacOptions
      }

      // TODO: currently IDEA doesn't support more finely-grained scopes,like `in (Compile, compile)
      //  so option root_option_in_compile_compile is not included
      //  IDEA-232043, SCL-11883, SCL-17020
      val root = moduleX("javac-options-per-module", Seq("root_option", "root_option_in_compile"))

      val module1 = moduleX("module1", Seq("module_1_option"))
      val module2 = moduleX("module2", Seq("module_2_option_in_compile"))
      val module3 = moduleX("module3", Seq())

      modules := Seq(
        root, module1, module2, module3
      )
    }
  )

  def testJavacSpecialOptionsForRootProject(): Unit = {
    runTest(
      new project("javac-special-options-for-root-project") {
        // no storing project level options
        javacOptions := Nil
        javaTargetBytecodeLevel := null
        javaLanguageLevel := SbtProjectStructureImportingTest_TransitiveProjectDependenciesDisabled.this.projectJdkLanguageLevel

        val root: module = new module("javac-special-options-for-root-project") {
          javaLanguageLevel := LanguageLevel.JDK_1_9
          javaTargetBytecodeLevel := "1.7"
          javacOptions := Seq(
            "-g:none",
            "-nowarn",
            "-deprecation",
            "-Werror"
          )
        }
        modules:= Seq(root)
      }
    )

    val compilerOptions = JavacConfiguration.getOptions(myProject, classOf[JavacConfiguration])
    val defaultCompilerOptions = new JpsJavaCompilerOptions

    assertEquals(defaultCompilerOptions.DEBUGGING_INFO, compilerOptions.DEBUGGING_INFO)
    assertEquals(defaultCompilerOptions.GENERATE_NO_WARNINGS, compilerOptions.GENERATE_NO_WARNINGS)
    assertEquals(defaultCompilerOptions.DEPRECATION, compilerOptions.DEPRECATION)
    assertEquals(defaultCompilerOptions.ADDITIONAL_OPTIONS_STRING, compilerOptions.ADDITIONAL_OPTIONS_STRING)
    assertEquals(defaultCompilerOptions.MAXIMUM_HEAP_SIZE, compilerOptions.MAXIMUM_HEAP_SIZE)
    assertEquals(defaultCompilerOptions.PREFER_TARGET_JDK_COMPILER, compilerOptions.PREFER_TARGET_JDK_COMPILER)
  }

  def testCompileOrder(): Unit = {
    runTest(new project("compile-order-unspecified") {
      modules := Seq(
        new module("compile-order-unspecified") {
          compileOrder := CompileOrder.Mixed
        },
        new module("compile-order-mixed") {
          compileOrder := CompileOrder.Mixed
        },
        new module("compile-order-scala-then-java") {
          compileOrder := CompileOrder.ScalaThenJava
        },
        new module("compile-order-java-then-scala") {
          compileOrder := CompileOrder.JavaThenScala
        }
      )
    })
  }

  def testSimpleProjectWithGeneratedSources(): Unit = runTest(
    new project("SimpleProjectWithGeneratedSources") {
      modules := Seq(
        new module("SimpleProjectWithGeneratedSources") {
          sources := Seq(
            "src/main/scala",
            "target/scala-2.13/src_managed/main",
            "target/myGenerated/main",
          )
          testSources := Seq(
            "src/test/scala",
            "target/scala-2.13/src_managed/test",
            "target/myGenerated/test",
          )
          resources := Seq(
            "src/main/resources",
            "target/scala-2.13/resource_managed/main"
          )
          testResources := Seq(
            "src/test/resources",
            "target/scala-2.13/resource_managed/test"
          )
          excluded := Seq("target")
        },
        new module("SimpleProjectWithGeneratedSources-build") {},
      )
    }
  )

  def testProjectWithModulesWithSameIdsAndNamesWithDifferentCase(): Unit = runTest(
    new project("ProjectWithModulesWithSameIdsAndNamesWithDifferentCase") {
      modules := Seq(
        new module ("ProjectWithModulesWithSameIdsAndNamesWithDifferentCase"),
        new module ("U_MY_MODULE_ID2", Array("same module name")),
        new module ("U_My_Module_Id1", Array("same module name")),
        new module ("U_my_module_id", Array("same module name")),
        new module ("X_MY_MODULE_ID2"),
        new module ("X_My_Module_Id1"),
        new module ("X_my_module_id"),
        new module ("Y_MY_MODULE_Name2"),
        new module ("Y_My_Module_Name1"),
        new module ("Y_my_module_name"),
        new module ("Z_MY_MODULE_Name2"),
        new module ("Z_My_Module_Name1"),
        new module ("Z_my_module_name"),
      )
    }
  )
}
