import sbt._


object Dependencies {
  val sbtStructureVersion = "4.0.0"
  val sbtStructureCore = "org.jetbrains" %% "sbt-structure-core" % sbtStructureVersion
  val sbtStructureExtractor012 = "org.jetbrains" % "sbt-structure-extractor-0-12" % sbtStructureVersion
  val sbtStructureExtractor013 = "org.jetbrains" % "sbt-structure-extractor-0-13" % sbtStructureVersion
  val sbtStructure = Seq(sbtStructureCore, sbtStructureExtractor012, sbtStructureExtractor013)
}