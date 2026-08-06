package org.jetbrains.plugins.scala.compiler.data

import org.jetbrains.jps.incremental.scala.remote.PathTranslator

import java.nio.file.{Files, Path, Paths}

case class SbtData(sbtInterfaceJar: Path,
                   compilerInterfaceJar: Path,
                   compilerBridges: SbtData.CompilerBridges,
                   interfacesHome: Path,
                   javaClassVersion: String) {
  private[data] def pluginJpsDirectory: Path = sbtInterfaceJar.getParent
}

object SbtData {

  def serialize(data: SbtData, translator: PathTranslator): Seq[String] = {
    import serialization.SerializationUtils.pathToString
    val SbtData(_, _, _, interfacesHome, javaClassVersion) = data

    Seq(
      pathToString(data.pluginJpsDirectory, translator),
      pathToString(interfacesHome, translator),
      javaClassVersion
    )
  }

  import Extractors.StringToPath

  def deserialize(strings: Seq[String]): Either[String, (SbtData, Seq[String])] = strings match {
    case StringToPath(pluginJpsDirectory) +:
      StringToPath(interfacesHome) +:
      javaClassVersion +:
      tail =>

      val Jars(sbtInterfaceJar, compilerInterfaceJar, compilerBridges) =
        Jars.fromPluginJpsDirectory(pluginJpsDirectory)
      Right(SbtData(sbtInterfaceJar, compilerInterfaceJar, compilerBridges, interfacesHome, javaClassVersion) -> tail)

    case args => Left(s"The arguments don't match the expected shape of CompilerData: ${args.mkString("[", ",", "]")}")
  }

  case class Jars(sbtInterfaceJar: Path, compilerInterfaceJar: Path, compilerBridges: SbtData.CompilerBridges)

  object Jars {
    def fromPluginJpsDirectory(pluginJpsDir: Path): Jars = Jars(
      sbtInterfaceJar = pluginJpsDir.resolve("sbt-interface.jar"),
      compilerInterfaceJar = pluginJpsDir.resolve("compiler-interface.jar"),
      compilerBridges = CompilerBridges(
        scala = ScalaSourceJars(
          _2_10 = pluginJpsDir.resolve("compiler-bridge-sources_2.10.jar"),
          _2_11 = pluginJpsDir.resolve("compiler-bridge-sources_2.11.jar"),
          _2_12 = pluginJpsDir.resolve("compiler-bridge-sources_2.12.jar"),
          _2_13 = pluginJpsDir.resolve("compiler-bridge-sources_2.13.jar")
        ),
        scala3 = Scala3Jars(
          _3_0 = pluginJpsDir.resolve("scala3-sbt-bridge_3.0.jar"),
          _3_1 = pluginJpsDir.resolve("scala3-sbt-bridge_3.1.jar"),
          _3_2 = pluginJpsDir.resolve("scala3-sbt-bridge_3.2.jar"),
          _3_3_1 = pluginJpsDir.resolve("scala3-sbt-bridge_3.3.1.jar"),
          _3_3 = pluginJpsDir.resolve("scala3-sbt-bridge_3.3.jar"),
          _3_4 = pluginJpsDir.resolve("scala3-sbt-bridge_3.4.jar")
        )
      )
    )
  }

  case class CompilerBridges(scala: ScalaSourceJars, scala3: Scala3Jars)

  /**
   * Contains sources of the scala compiler bridges.
   * We must compile sources to use bridges.
   */
  case class ScalaSourceJars(_2_10: Path, _2_11: Path, _2_12: Path, _2_13: Path)

  /**
   * Contains already compiled dotty/scala3 compiler bridges.
   */
  case class Scala3Jars(_3_0: Path, _3_1: Path, _3_2: Path, _3_3_1: Path, _3_3: Path, _3_4: Path)

  val compilerInterfacesKey = "scala.compiler.interfaces.dir"

  private def compilerInterfacesDir(compileServerSystemDir: Path): Path = {
    def defaultDir: Path =
      compileServerSystemDir.resolve("scala-compiler-interfaces")

    val customPath = Option(System.getProperty(compilerInterfacesKey))
    customPath.map(Paths.get(_)).getOrElse(defaultDir)
  }

  def from(pluginJpsRoot: Path, javaClassVersion: String, compileServerSystemDir: Path): Either[String, SbtData] =
    for {
      sbtHome <- Either.cond(Files.exists(pluginJpsRoot), pluginJpsRoot, "Scala plugin jps directory does not exist: " + pluginJpsRoot)
      Jars(sbtInterfaceJar, compilerInterfaceJar, compilerBridges) = Jars.fromPluginJpsDirectory(sbtHome)
    } yield {
      import org.jetbrains.plugins.scala.compiler.buildinfo.BuildInfo.{sbtVersion, zincVersion}
      val directoryName = s"scala-compiler-bridges_${sbtVersion}_$zincVersion"
      val interfacesHome = compilerInterfacesDir(compileServerSystemDir).resolve(directoryName)
      SbtData(sbtInterfaceJar, compilerInterfaceJar, compilerBridges, interfacesHome, javaClassVersion)
    }
}
