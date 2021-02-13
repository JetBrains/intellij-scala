package org.jetbrains.idea.devkit.scala.project

import junit.framework.TestCase
import org.junit.Assert._

import org.jetbrains.sbt.project.template.AbstractArchivedSbtProjectBuilder._

class SbtIdeaPluginProjectBuilderTest extends TestCase {

  private val BUILD_SBT =
    """
      |import org.jetbrains.sbtidea.Keys._
      |
      |lazy val myAwesomeFramework =
      |  project.in(file("."))
      |    .enablePlugins(SbtIdeaPlugin)
      |    .settings(
      |      version := "0.0.1-SNAPSHOT",
      |      scalaVersion := "2.13.2",
      |      ThisBuild / intellijPluginName := "My Awesome Framework",
      |      ThisBuild / intellijBuild      := "203.7148.15",
      |      ThisBuild / intellijPlatform   := IntelliJPlatform.IdeaCommunity,
      |      Global    / intellijAttachSources := true,
      |      Compile   / javacOptions       ++= "--release" :: "11" :: Nil,
      |      unmanagedResourceDirectories in Compile += baseDirectory.value / "resources",
      |      unmanagedResourceDirectories in Test    += baseDirectory.value / "testResources",
      |    )
      |""".stripMargin

  private val PLUGIN_XML =
    """
      |<idea-plugin>
      |    <id>me.and.co.my.awesome.framework</id>
      |    <name>My Awesome Framework</name>
      |    <version>0.0.1-SNAPSHOT</version>
      |    <vendor>Me and Co</vendor>
      |    <description>A new plugin for IntelliJ platform written in Scala and built with SBT</description>
      |    <idea-version since-build="203.0" until-build="123123"/>
      |</idea-plugin>
      |""".stripMargin

  private val PLUGINS_SBT = """addSbtPlugin("org.jetbrains" % "sbt-idea-plugin" % "3.9.0")"""

  private def doTest(content: String, replacements: Map[String, String]): Unit = {
    val result = replacePatterns(content, replacements)
    result match {
      case Left(errors) =>
        fail(s"Replacement failed:\n${errors.mkString("\n")}")
      case Right(value) =>
        println(value)
    }
  }

  def testBuildSbt(): Unit = {
    val replacements = Map(
      "(^.+lazy\\s+val\\s+)(\\w+)(\\s+=.+$)"  -> "projectValName",
      "scalaVersion".keyInitQuoted  -> "scalaVersion",
      "ThisBuild / intellijPluginName".keyInitQuoted       -> "pluginName",
      "ThisBuild / intellijBuild".keyInitQuoted    -> "intelliJBuild",
      "ThisBuild / intellijPlatform".keyInit -> "platformName"
    )
    doTest(BUILD_SBT, replacements)
  }

  def testPluginXml(): Unit = {
    val replacements = Map(
      "id".tagBody  -> "IDIDIDI",
      "name".tagBody  -> "NAME",
      "vendor".tagBody       -> "VENDOR",
      "idea-version/since-build".emptyTagAttr    -> "918291829",
      "idea-version/until-build".emptyTagAttr    -> "----",
    )
    doTest(PLUGIN_XML, replacements)
  }

  def testPluginsSbt(): Unit = {
    val replacements = Map(
      """(^.*addSbtPlugin\(\s*"org.jetbrains"\s*%\s*"sbt-idea-plugin"\s*%\s*")([^"]+)("\s*\))""" -> "VERSION"
    )
    doTest(PLUGINS_SBT, replacements)
  }
}