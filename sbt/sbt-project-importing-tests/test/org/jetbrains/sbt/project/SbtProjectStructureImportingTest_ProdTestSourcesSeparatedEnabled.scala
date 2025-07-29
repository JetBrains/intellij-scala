package org.jetbrains.sbt.project

import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.project.external.JdkByName
import org.jetbrains.sbt.project.ProjectStructureDsl.{contentRoots, dependency, excluded, isExported, javaLanguageLevel, javaTargetBytecodeLevel, javacOptions, libraries, library, libraryDependencies, module, moduleDependencies, moduleFileDirectoryPath, modules, project, resources, sbtBuildURI, sbtProjectId, sdk, testResources, testSources}

import java.net.URI

// TODO: ensure there is test for SCL-19673 for BSO external system as well
/**
 * @see [[SbtProjectStructureImportingTest]]
 */
final class SbtProjectStructureImportingTest_ProdTestSourcesSeparatedEnabled
  extends SbtProjectStructureImportingTestBase_ProdTestSourcesSeparated {

  // note: this test is for the case in which an additional project is linked to the project.
  // The linked project is project "simple". The ideProject is generated from "twoLinkedProjects" project
  def testTwoLinkedProjects(): Unit = {
    val originalProjectName = "twoLinkedProjects"
    val linkedProjectName = "simple"
    val expectedScalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("2.13.14")
    val linkedSbtProjectPath = generateTestProjectPath(linkedProjectName)
    linkSbtProject(linkedSbtProjectPath, prodTestSourcesSeparated = true)
    val siProjectPath = FileUtil.toSystemIndependentName(getProjectPath)
    val siLinkedSbtProjectPath = FileUtil.toSystemIndependentName(linkedSbtProjectPath)
    runTest(
      new project("testTwoLinkedProjects") {
        modules := Seq(
          new module(originalProjectName) {
            contentRoots += siProjectPath
            excluded := Seq("target")
            moduleFileDirectoryPath := "twoLinkedProjects"
          },
          new module(s"$originalProjectName.main") {
            contentRoots := Seq(s"$siProjectPath/src/main", s"$siProjectPath/target/scala-2.13/src_managed/main", s"$siProjectPath/target/scala-2.13/resource_managed/main")
            ProjectStructureDsl.sources := Seq("scala", "java")
            resources := Seq("resources")
            libraryDependencies := expectedScalaLibraries
            moduleFileDirectoryPath := "twoLinkedProjects"
          },
          new module(s"$originalProjectName.test") {
            contentRoots := Seq(s"$siProjectPath/src/test", s"$siProjectPath/target/scala-2.13/src_managed/test", s"$siProjectPath/target/scala-2.13/resource_managed/test")
            testSources := Seq("scala", "java")
            testResources := Seq("resources")
            libraryDependencies := expectedScalaLibraries
            moduleFileDirectoryPath := "twoLinkedProjects"
          },
          new module(s"$originalProjectName.$originalProjectName-build") {
            ProjectStructureDsl.sources := Seq("")
            excluded := Seq("project/target", "target")
            moduleFileDirectoryPath := "twoLinkedProjects"
          },
          new module(linkedProjectName) {
            contentRoots += siLinkedSbtProjectPath
            excluded := Seq("target")
            moduleFileDirectoryPath := "simple"
          },
          new module(s"$linkedProjectName.main") {
            contentRoots := Seq(s"$siLinkedSbtProjectPath/src/main", s"$siLinkedSbtProjectPath/target/scala-2.13/src_managed/main", s"$siLinkedSbtProjectPath/target/scala-2.13/resource_managed/main")
            ProjectStructureDsl.sources := Seq("scala", "java")
            resources := Seq("resources")
            libraryDependencies := expectedScalaLibraries
            moduleFileDirectoryPath := "simple"
          },
          new module(s"$linkedProjectName.test") {
            contentRoots := Seq(s"$siLinkedSbtProjectPath/src/test", s"$siLinkedSbtProjectPath/target/scala-2.13/src_managed/test", s"$siLinkedSbtProjectPath/target/scala-2.13/resource_managed/test")
            testSources := Seq("scala", "java")
            testResources := Seq("resources")
            libraryDependencies := expectedScalaLibraries
            moduleFileDirectoryPath := "simple"
          },
          new module(s"$linkedProjectName.$linkedProjectName-build") {
            ProjectStructureDsl.sources := Seq("")
            excluded := Seq("project/target", "target")
            moduleFileDirectoryPath := "simple"
          }
        )
      }
    )
    assertDirectoryCompletionVariantsForProjectPaths(
      DefaultSbtContentRootsScala213,
      DefaultMainSbtContentRootsScala213,
      DefaultTestSbtContentRootsScala213,
      linkedSbtProjectPath,
      getProjectPath
    )
  }

  /**
   * SCL-13600: generate all modules when there is a duplicate project id in the sbt build
   * due to references to different builds, or multiple sbt projects being imported independently from IDEA
   */
  def testSCL13600(): Unit = runTest(
    new project("root") {
      val buildURI: URI = getTestProjectDir.getCanonicalFile.toURI

      val rootC1: module = new module("Build C1 Name") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("c1/")
        moduleDependencies ++= Seq(
          new dependency(rootC1Main) {
            isExported := false
          },
          new dependency(rootC1Test) {
            isExported := false
          }
        )
      }
      lazy val rootC1Main: module = new module("Build C1 Name.main") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("c1/")
        moduleDependencies := Seq()
      }
      lazy val rootC1Test: module = new module("Build C1 Name.test") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("c1/")
        moduleDependencies := Seq(rootC1Main)
      }
      val rootC2: module = new module("Build C2 Name") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("c2/")
        moduleDependencies ++= Seq(
          new dependency(rootC2Main) {
            isExported := false
          },
          new dependency(rootC2Test) {
            isExported := false
          }
        )
      }
      lazy val rootC2Main: module = new module("Build C2 Name.main") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("c2/")
        moduleDependencies := Seq()
      }
      lazy val rootC2Test: module = new module("Build C2 Name.test") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("c2/")
        moduleDependencies := Seq(rootC2Main)
      }
      val rootC3: module = new module("suffix2.root") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("prefix1/prefix2/c3/suffix1/suffix2/")
        moduleDependencies ++= Seq(
          new dependency(rootC3Main) {
            isExported := false
          },
          new dependency(rootC3Test) {
            isExported := false
          }
        )
      }
      lazy val rootC3Main: module = new module("suffix2.root.main") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("prefix1/prefix2/c3/suffix1/suffix2/")
        moduleDependencies := Seq()
      }
      lazy val rootC3Test: module = new module("suffix2.root.test") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("prefix1/prefix2/c3/suffix1/suffix2/")
        moduleDependencies := Seq(rootC3Main)
      }
      val rootC4: module = new module("suffix1.suffix2.root") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("prefix1/prefix2/c4/suffix1/suffix2/")
        moduleDependencies ++= Seq(
          new dependency(rootC4Main) {
            isExported := false
          },
          new dependency(rootC4Test) {
            isExported := false
          }
        )
      }
      lazy val rootC4Main: module = new module("suffix1.suffix2.root.main") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("prefix1/prefix2/c4/suffix1/suffix2/")
        moduleDependencies := Seq()
      }
      lazy val rootC4Test: module = new module("suffix1.suffix2.root.test") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("prefix1/prefix2/c4/suffix1/suffix2/")
        moduleDependencies := Seq(rootC4Main)
      }
      val root: module = new module("root") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI
        moduleDependencies ++= Seq(
          new dependency(rootMain) {
            isExported := false
          },
          new dependency(rootTest) {
            isExported := false
          }
        )
      }
      lazy val rootMain: module = new module("root.main") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI
        moduleDependencies := Seq(
          new dependency(rootC1Main) {isExported := false },
          new dependency(rootC2Main) {isExported := false },
          new dependency(rootC3Main) {isExported := false },
          new dependency(rootC4Main) {isExported := false },
        )
      }
      lazy val rootTest: module = new module("root.test") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI
        moduleDependencies := Seq(
          new dependency(rootMain) {isExported := false },
          new dependency(rootC1Main) {isExported := false },
          new dependency(rootC2Main) {isExported := false },
          new dependency(rootC3Main) {isExported := false },
          new dependency(rootC4Main) {isExported := false },
        )
      }

      val modulesFromRoot: Seq[module] =
        createModuleWithSourceSet("project1InRootBuild", Array("root")) ++
          createModuleWithSourceSet("project2InRootBuild", Array("root")) ++
          createModuleWithSourceSet("project3InRootBuildWithSameName", Array("root", "same name in root build")) ++
          createModuleWithSourceSet("project4InRootBuildWithSameName", Array("root", "same name in root build")) ++
          createModuleWithSourceSet("project5InRootBuildWithSameGlobalName", Array("root", "same global name")) ++
          createModuleWithSourceSet("project6InRootBuildWithSameGlobalName", Array("root", "same global name"))

      val modulesFromC1: Seq[module] =
        Seq(rootC1,rootC1Main, rootC1Test) ++
          createModuleWithSourceSet("project1InC1", Array("Build C1 Name")) ++
          createModuleWithSourceSet("project2InC1", Array("Build C1 Name")) ++
          createModuleWithSourceSet("project3InC1WithSameName", Array("Build C1 Name", "same name in c1")) ++
          createModuleWithSourceSet("project4InC1WithSameName", Array("Build C1 Name", "same name in c1")) ++
          createModuleWithSourceSet("project5InC1WithSameGlobalName", Array("Build C1 Name", "same global name")) ++
          createModuleWithSourceSet("project6InC1WithSameGlobalName", Array("Build C1 Name", "same global name"))

      val modulesFromC2: Seq[module] =
        Seq(rootC2, rootC2Main, rootC2Test) ++
          createModuleWithSourceSet("project1InC2", Array("Build C2 Name")) ++
          createModuleWithSourceSet("project2InC2", Array("Build C2 Name")) ++
          createModuleWithSourceSet("project3InC2WithSameName", Array("Build C2 Name", "same name in c2")) ++
          createModuleWithSourceSet("project4InC2WithSameName", Array("Build C2 Name", "same name in c2")) ++
          createModuleWithSourceSet("project5InC2WithSameGlobalName", Array("Build C2 Name", "same global name")) ++
          createModuleWithSourceSet("project6InC2WithSameGlobalName", Array("Build C2 Name", "same global name"))

      val modulesFromC3: Seq[module] =
        Seq(rootC3, rootC3Main, rootC3Test) ++
          createModuleWithSourceSet("project1InC3", Array("suffix2.root")) ++
          createModuleWithSourceSet("project2InC3", Array("suffix2.root")) ++
          createModuleWithSourceSet("project3InC3WithSameName", Array("suffix2.root", "same name in c3")) ++
          createModuleWithSourceSet("project4InC3WithSameName", Array("suffix2.root", "same name in c3")) ++
          createModuleWithSourceSet("project5InC3WithSameGlobalName", Array("suffix2.root", "same global name")) ++
          createModuleWithSourceSet("project6InC3WithSameGlobalName", Array("suffix2.root", "same global name"))

      val modulesFromC4: Seq[module] =
        Seq(rootC4, rootC4Main, rootC4Test) ++
          createModuleWithSourceSet("project1InC4", Array("suffix1.suffix2.root")) ++
          createModuleWithSourceSet("project2InC4", Array("suffix1.suffix2.root")) ++
          createModuleWithSourceSet("project3InC4WithSameName", Array("suffix1.suffix2.root", "same name in c4")) ++
          createModuleWithSourceSet("project4InC4WithSameName", Array("suffix1.suffix2.root", "same name in c4")) ++
          createModuleWithSourceSet("project5InC4WithSameGlobalName", Array("suffix1.suffix2.root", "same global name")) ++
          createModuleWithSourceSet("project6InC4WithSameGlobalName", Array("suffix1.suffix2.root", "same global name"))


      modules := Seq(root, rootMain, rootTest) ++:
        modulesFromRoot ++:
        modulesFromC1 ++:
        modulesFromC2 ++:
        modulesFromC3 ++:
        modulesFromC4
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

    val projectName = "java-language-level-and-target-byte-code-level"
    try runTest(
      new project(projectName) {
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

        def moduleXWithMainTestModules(name: String, source: LanguageLevel, @Nullable target: String): Seq[module] = {
          Seq(
            moduleX(name, sdkLanguageLevel, null),
            moduleX(s"$name.main", source, target),
            moduleX(s"$name.test", source, target)
          )
        }

        val sdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_1_9

        val root = moduleXWithMainTestModules("java-language-level-and-target-byte-code-level", sdkLanguageLevel, null)

        // Module naming: `source_target_release`
        // `x` means option is missing
        val module_x_x_x = moduleXWithMainTestModules(s"$projectName.module_x_x_x", sdkLanguageLevel, null)

        val module_8_8_x   = moduleXWithMainTestModules(s"$projectName.module_8_8_x", LanguageLevel.JDK_1_8, "8")
        val module_8_11_x  = moduleXWithMainTestModules(s"$projectName.module_8_11_x", LanguageLevel.JDK_1_8, "11")
        val module_11_8_x  = moduleXWithMainTestModules(s"$projectName.module_11_8_x", LanguageLevel.JDK_11, "8")
        val module_11_11_x = moduleXWithMainTestModules(s"$projectName.module_11_11_x", LanguageLevel.JDK_11, "11")

        // no explicit target: javac will use source level by default
        val module_8_x_x  = moduleXWithMainTestModules(s"$projectName.module_8_x_x", LanguageLevel.JDK_1_8, null)
        val module_11_x_x = moduleXWithMainTestModules(s"$projectName.module_11_x_x", LanguageLevel.JDK_11, null)
        val module_14_x_x = moduleXWithMainTestModules(s"$projectName.module_14_x_x", LanguageLevel.JDK_14, null)
        val module_15_x_x = moduleXWithMainTestModules(s"$projectName.module_15_x_x", LanguageLevel.JDK_15, null)

        val module_x_8_x  = moduleXWithMainTestModules(s"$projectName.module_x_8_x", sdkLanguageLevel, "8")
        val module_x_11_x = moduleXWithMainTestModules(s"$projectName.module_x_11_x", sdkLanguageLevel, "11")

        val module_x_x_8  = moduleXWithMainTestModules(s"$projectName.module_x_x_8", LanguageLevel.JDK_1_8, "8")
        val module_x_x_11 = moduleXWithMainTestModules(s"$projectName.module_x_x_11", LanguageLevel.JDK_11, "11")

        // Java preview features
        // NOTE: IntelliJ API supports only 2 last preview versions of java language level (in com.intellij.pom.java.LanguageLevel)
        // When a new version of Java releases and IDEA supports it, we should update this test
        //
        // no explicit target: javac will use source level by default
        val module_8_x_x_preview  = moduleXWithMainTestModules(s"$projectName.module_8_x_x_preview", LanguageLevel.JDK_1_8, null) // no preview for Java 8
        val module_11_x_x_preview = moduleXWithMainTestModules(s"$projectName.module_11_x_x_preview", LanguageLevel.JDK_11, null) // no preview for Java 11
        val module_14_x_x_preview = moduleXWithMainTestModules(s"$projectName.module_14_x_x_preview", LanguageLevel.JDK_14, null) // no preview for Java 11
        val module_20_x_x_preview = moduleXWithMainTestModules(s"$projectName.module_20_x_x_preview", LanguageLevel.JDK_20_PREVIEW, null)

        val module_x_x_8_preview  = moduleXWithMainTestModules(s"$projectName.module_x_x_8_preview", LanguageLevel.JDK_1_8, "8")
        val module_x_x_11_preview = moduleXWithMainTestModules(s"$projectName.module_x_x_11_preview", LanguageLevel.JDK_11, "11")
        val module_x_x_14_preview = moduleXWithMainTestModules(s"$projectName.module_x_x_14_preview", LanguageLevel.JDK_14, "14")
        val module_x_x_20_preview = moduleXWithMainTestModules(s"$projectName.module_x_x_20_preview", LanguageLevel.JDK_20_PREVIEW, "20")

        modules :=
          root ++
            module_x_x_x ++
            module_8_8_x ++ module_8_11_x ++ module_11_8_x ++ module_11_11_x ++
            module_8_x_x ++ module_11_x_x ++ module_14_x_x ++ module_15_x_x ++
            module_x_8_x ++ module_x_11_x ++
            module_x_x_8 ++ module_x_x_11 ++
            module_8_x_x_preview ++ module_11_x_x_preview ++ module_14_x_x_preview ++ module_20_x_x_preview ++
            module_x_x_8_preview ++ module_x_x_11_preview ++ module_x_x_14_preview ++ module_x_x_20_preview

      }
    ) finally {
      inWriteAction {
        ProjectJdkTable.getInstance.removeJdk(projectSdk9)
      }
    }
  }

  def testMultiBuildProjectWithSpecialCharactersInRootProjectNames(): Unit = runTest(
    new project("ro//o/t\\") {
      val buildURI: URI = getTestProjectDir.getCanonicalFile.toURI

      val rootC1: module = new module("Build__1_N_ame") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("c1/")
        moduleDependencies ++= Seq(
          new dependency(rootC1Main) {
            isExported := false
          },
          new dependency(rootC1Test) {
            isExported := false
          }
        )
      }
      lazy val rootC1Main: module = new module("Build__1_N_ame.main") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("c1/")
        moduleDependencies := Seq()
      }
      lazy val rootC1Test: module = new module("Build__1_N_ame.test") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI.resolve("c1/")
        moduleDependencies := Seq(rootC1Main)
      }
      val root: module = new module("ro__o_t_") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI
        moduleDependencies ++= Seq(
          new dependency(rootMain) {
            isExported := false
          },
          new dependency(rootTest) {
            isExported := false
          }
        )
      }
      lazy val rootMain: module = new module("ro__o_t_.main") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI
        moduleDependencies ++= Seq(
          new dependency(rootC1Main) { isExported := false },
        )
      }
      lazy val rootTest: module = new module("ro__o_t_.test") {
        sbtProjectId := "root"
        sbtBuildURI := buildURI
        moduleDependencies ++= Seq(
          new dependency(rootMain) { isExported := false },
          new dependency(rootC1Main) { isExported := false }
        )
      }

      val modulesRoot: Seq[module] =
        Seq(root, rootMain, rootTest) ++
          createModuleWithSourceSet("foo", Array("ro__o_t_"))

      val modulesC1: Seq[module] =
        Seq(rootC1, rootC1Main, rootC1Test) ++
          createModuleWithSourceSet("foo", Array("Build__1_N_ame"))

      modules := modulesRoot ++ modulesC1
    }
  )

  // SBT guarantees us that project ids inside builds are unique. In IDEA in the internal module name all "/" are replaced with "_" and it could happen that in one build
  // the name of one project would be e.g. ro/t and the other one would be ro_t and for SBT project ids uniqueness would be maintained but not for IDEA.
  // In the case of such deduplication, IDEA will add a ~<number> suffix to each sbt source set module (main/test) or sbt nested module (the parent module for main/test).
  // It's done by explicitly setting the ModuleNameDeduplicationStrategy.NUMBER_SUFFIX in these modules.
  def testMultiBuildProjectWithTheSameProjectIdFromIDEAPerspective(): Unit = runTest(
    new project("multiBuildProjectWithTheSameProjectIdFromIDEAPerspective") {
      lazy val scalaLibraries: Seq[library] = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("2.13.14")
      libraries := scalaLibraries

      val buildURI: URI = getTestProjectDir.getCanonicalFile.toURI

      lazy val c1: module = new module("c1") {
        contentRoots := Seq(getProjectPath + "/c1")
        sbtProjectId := "c1"
        sbtBuildURI := buildURI.resolve("c1/")
        libraryDependencies := Seq()
      }
      lazy val c1Main: module = new module("c1.main") {
        contentRoots := Seq(s"$getProjectPath/c1/src/main", s"$getProjectPath/c1/target/scala-2.13/src_managed/main", s"$getProjectPath/c1/target/scala-2.13/resource_managed/main")
        sbtProjectId := "c1"
        sbtBuildURI := buildURI.resolve("c1/")
        libraryDependencies := scalaLibraries
      }
      lazy val c1Test: module = new module("c1.test") {
        contentRoots := Seq(s"$getProjectPath/c1/src/test", s"$getProjectPath/c1/target/scala-2.13/src_managed/test", s"$getProjectPath/c1/target/scala-2.13/resource_managed/test")
        sbtProjectId := "c1"
        sbtBuildURI := buildURI.resolve("c1/")
        libraryDependencies := scalaLibraries
      }
      val c1Root1: module = new module("ro_t", Array("c1")) {
        libraryDependencies := Seq()
        sbtProjectId := "mod1"
        sbtBuildURI := buildURI.resolve("c1/")
      }
      val c1Root1Main: module = new module("ro_t.main", Array("c1")) {
        libraryDependencies := scalaLibraries
        sbtProjectId := "mod1"
        sbtBuildURI := buildURI.resolve("c1/")
      }
      val c1Root1Test: module = new module("ro_t.test", Array("c1")) {
        libraryDependencies := scalaLibraries
        sbtProjectId := "mod1"
        sbtBuildURI := buildURI.resolve("c1/")
      }
      val c1Root2: module = new module("ro_t~1", Array("c1")) {
        libraryDependencies := Seq()
        sbtProjectId := "mod2"
        sbtBuildURI := buildURI.resolve("c1/")
      }
      val c1Root2Main: module = new module("ro_t~1.main", Array("c1")) {
        libraryDependencies := scalaLibraries
        sbtProjectId := "mod2"
        sbtBuildURI := buildURI.resolve("c1/")
      }

      val c1Root2Test: module = new module("ro_t~1.test", Array("c1")) {
        libraryDependencies := scalaLibraries
        sbtProjectId := "mod2"
        sbtBuildURI := buildURI.resolve("c1/")
      }

      lazy val root: module = new module("multiBuildProjectWithTheSameProjectIdFromIDEAPerspective") {
        contentRoots := Seq(getProjectPath)
        sbtProjectId := "multiBuildProjectWithTheSameProjectIdFromIDEAPerspective"
        sbtBuildURI := buildURI
        libraryDependencies := Seq()
      }
      lazy val rootMain: module = new module("multiBuildProjectWithTheSameProjectIdFromIDEAPerspective.main") {
        contentRoots := Seq(s"$getProjectPath/src/main", s"$getProjectPath/target/scala-2.13/src_managed/main", s"$getProjectPath/target/scala-2.13/resource_managed/main")
        sbtProjectId := "multiBuildProjectWithTheSameProjectIdFromIDEAPerspective"
        sbtBuildURI := buildURI
        libraryDependencies := scalaLibraries
        moduleDependencies += new dependency(c1Main) { isExported := false }
      }
      lazy val rootTest: module = new module("multiBuildProjectWithTheSameProjectIdFromIDEAPerspective.test") {
        contentRoots := Seq(s"$getProjectPath/src/test", s"$getProjectPath/target/scala-2.13/src_managed/test", s"$getProjectPath/target/scala-2.13/resource_managed/test")
        sbtProjectId := "multiBuildProjectWithTheSameProjectIdFromIDEAPerspective"
        sbtBuildURI := buildURI
        libraryDependencies := scalaLibraries
        moduleDependencies ++= Seq(
          new dependency(c1Main) { isExported := false },
          new dependency(rootMain) { isExported := false },
        )
      }

      val c1Modules: Seq[module] = Seq(
        c1, c1Main, c1Test,
        c1Root1, c1Root1Main, c1Root1Test,
        c1Root2, c1Root2Main, c1Root2Test,
      )

      modules := Seq(root, rootMain, rootTest) ++ c1Modules
    }
  )
}
