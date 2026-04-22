package org.jetbrains.sbt.shell

import org.jetbrains.sbt.project.data.SbtModuleData
import org.jetbrains.sbt.shell.SbtBuildCommandsFactory.{ModuleWithScopes, SbtScope}
import org.jetbrains.sbt.{SbtUtil, SbtVersion}
import org.junit.Assert.assertEquals
import org.junit.Test

import java.net.URI
import java.nio.file.Path

/**
 * Lightweight unit tests for [[org.jetbrains.sbt.shell.SbtBuildCommandsFactory]].
 *
 * The tests simply test the constructed list of sbt commands used when delegating a build to sbt shell
 */
class SbtBuildCommandFactoryTest {

  private val sbtVersion_0_13 = SbtVersion.Latest.Sbt_0_13
  private val sbtVersion_1 = SbtVersion.Latest.Sbt_1_12
  private val sbtVersion_2 = SbtVersion.Latest.Sbt_2

  @Test
  def createBuildCommands_DeduplicateScopesForBuildProjectTasks(): Unit = {
    val root = moduleData("root", "file:///root/")

    val commands = SbtBuildCommandsFactory.createBuildCommandsInner(sbtVersion_1, Seq(
      ModuleWithScopes(root, Set(SbtScope.Main, SbtScope.Test)),
      ModuleWithScopes(root, Set(SbtScope.Main)),
      ModuleWithScopes(root, Set(SbtScope.Test)),
    ))

    assertEquals(
      Set(
        s"${SbtUtil.makeSbtProjectId(root)}/products",
        s"${SbtUtil.makeSbtProjectId(root)}/Test/products",
      ),
      commands.toSet,
    )
  }

  @Test
  def createBuildCommands_DeduplicateScopesForBuildProjectTasksWithMultipleRootsAndModules(): Unit = {
    val rootInBuildOne = moduleData("root", "file:///build-one/")
    val appInBuildOne = moduleData("app", "file:///build-one/")
    val rootInBuildTwo = moduleData("root", "file:///build-two/")
    val integrationInBuildTwo = moduleData("integration", "file:///build-two/")

    val commands = SbtBuildCommandsFactory.createBuildCommandsInner(sbtVersion_1, Seq(
      ModuleWithScopes(rootInBuildOne, Set(SbtScope.Main)),
      ModuleWithScopes(rootInBuildOne, Set(SbtScope.Test)),
      ModuleWithScopes(appInBuildOne, Set(SbtScope.Main)),
      ModuleWithScopes(appInBuildOne, Set(SbtScope.Test)),
      ModuleWithScopes(appInBuildOne, Set(SbtScope.Main, SbtScope.Test)),
      ModuleWithScopes(rootInBuildTwo, Set(SbtScope.Test)),
      ModuleWithScopes(rootInBuildTwo, Set(SbtScope.Main)),
      ModuleWithScopes(rootInBuildTwo, Set(SbtScope.Test)),
      ModuleWithScopes(integrationInBuildTwo, Set(SbtScope.Test)),
    ))

    assertEquals(
      Set(
        s"${SbtUtil.makeSbtProjectId(rootInBuildOne)}/products",
        s"${SbtUtil.makeSbtProjectId(rootInBuildOne)}/Test/products",
        s"${SbtUtil.makeSbtProjectId(appInBuildOne)}/products",
        s"${SbtUtil.makeSbtProjectId(appInBuildOne)}/Test/products",
        s"${SbtUtil.makeSbtProjectId(rootInBuildTwo)}/products",
        s"${SbtUtil.makeSbtProjectId(rootInBuildTwo)}/Test/products",
        s"${SbtUtil.makeSbtProjectId(integrationInBuildTwo)}/Test/products",
      ),
      commands.toSet,
    )
  }

  @Test
  def createBuildCommands_ForMainScopeOnly(): Unit = {
    val root = moduleData("root", "file:///root/")

    val commands = SbtBuildCommandsFactory.createBuildCommandsInner(
      sbtVersion = sbtVersion_1,
      scopesPerModule = Seq(ModuleWithScopes(root, Set(SbtScope.Main))),
    )

    assertEquals(
      Set(s"${SbtUtil.makeSbtProjectId(root)}/products"),
      commands.toSet,
    )
  }

  @Test
  def createBuildCommands_ForTestScopeOnly(): Unit = {
    val root = moduleData("root", "file:///root/")

    val commands = SbtBuildCommandsFactory.createBuildCommandsInner(
      sbtVersion = sbtVersion_1,
      scopesPerModule = Seq(ModuleWithScopes(root, Set(SbtScope.Test))),
    )

    assertEquals(
      Set(s"${SbtUtil.makeSbtProjectId(root)}/Test/products"),
      commands.toSet,
    )
  }

  @Test
  def createBuildCommands_ForMultipleProjects(): Unit = {
    val rootInBuildOne = moduleData("root", "file:///build-one/")
    val rootInBuildTwo = moduleData("root", "file:///build-two/")

    val commands = SbtBuildCommandsFactory.createBuildCommandsInner(
      sbtVersion = sbtVersion_1,
      scopesPerModule = Seq(
        ModuleWithScopes(rootInBuildOne, Set(SbtScope.Main, SbtScope.Test)),
        ModuleWithScopes(rootInBuildTwo, Set(SbtScope.Main, SbtScope.Test)),
      ),
    )

    assertEquals(
      Set(
        s"${SbtUtil.makeSbtProjectId(rootInBuildOne)}/products",
        s"${SbtUtil.makeSbtProjectId(rootInBuildOne)}/Test/products",
        s"${SbtUtil.makeSbtProjectId(rootInBuildTwo)}/products",
        s"${SbtUtil.makeSbtProjectId(rootInBuildTwo)}/Test/products",
      ),
      commands.toSet,
    )
  }

  @Test
  def createBuildCommands_UseSlashSyntaxForModernSbtVersions(): Unit = {
    val root = moduleData("root", "file:///root/")

    val commandsForSbt112 = SbtBuildCommandsFactory.createBuildCommandsInner(
      sbtVersion = sbtVersion_1,
      scopesPerModule = Seq(ModuleWithScopes(root, Set(SbtScope.Test))),
    )
    val commandsForSbt2 = SbtBuildCommandsFactory.createBuildCommandsInner(
      sbtVersion = sbtVersion_2,
      scopesPerModule = Seq(ModuleWithScopes(root, Set(SbtScope.Test))),
    )

    assertEquals(Set(s"${SbtUtil.makeSbtProjectId(root)}/Test/products"), commandsForSbt112.toSet)
    assertEquals(Set(s"${SbtUtil.makeSbtProjectId(root)}/Test/products"), commandsForSbt2.toSet)
  }

  @Test
  def createBuildCommands_UseLegacySyntaxForSbt013(): Unit = {
    val root = moduleData("root", "file:///root/")

    val commands = SbtBuildCommandsFactory.createBuildCommandsInner(
      sbtVersion = sbtVersion_0_13,
      scopesPerModule = Seq(ModuleWithScopes(root, Set(SbtScope.Test))),
    )

    assertEquals(Set(s"${SbtUtil.makeSbtProjectId(root)}/test:products"), commands.toSet)
  }

  private def moduleData(id: String, buildUri: String): SbtModuleData =
    SbtModuleData(
      id = id,
      buildURI = URI.create(buildUri),
      baseDirectory = Path.of(System.getProperty("java.io.tmpdir"), "sbt-build-command-planner-tests", id),
    )
}
