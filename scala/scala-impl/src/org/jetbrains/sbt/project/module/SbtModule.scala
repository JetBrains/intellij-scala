package org.jetbrains.sbt
package project.module

import java.net.URI

import com.intellij.openapi.components._
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.resolvers.SbtResolver

/**
 * @author Pavel Fatin
 */
object SbtModule {
  @Deprecated
  private val ImportsKey = "sbt.imports"

  @Deprecated
  private val Delimiter = ", "

  @Deprecated
  private val ResolversKey = "sbt.resolvers"

  private def getState(module: Module): SbtModule.ModuleState =
    module.getComponent(classOf[ModuleState])

  def getImportsFrom(module: Module): Seq[String] =
    Option(getState(module).imports)
      .filter(_.nonEmpty)
      .orElse(Option(module.getOptionValue(ImportsKey))) // TODO remove in 2018.3+
      .filter(_.nonEmpty)
      .map(v => unsubstituteOptionString(v).split(Delimiter).toSeq)
      .getOrElse(Sbt.DefaultImplicitImports)

  def setImportsTo(module: Module, imports: Seq[String]): Unit = {
    val v = substituteOptionString(imports.mkString(Delimiter))
    module.setOption(ImportsKey, v) // TODO remove in 2018.3+
    getState(module).imports = v
  }

  def getResolversFrom(module: Module): Set[SbtResolver] =
    Option(getState(module).resolvers)
      .filter(_.nonEmpty)
      .orElse(Option(module.getOptionValue(ResolversKey))) // TODO remove in 2018.3+
      .map { str =>
        str.split(Delimiter).map(SbtResolver.fromString).collect {
          case Some(r) => r
        }.toSet
      }.getOrElse(Set.empty)

  def setResolversTo(module: Module, resolvers: Set[SbtResolver]): Unit = {
    val v = resolvers.map(_.toString).mkString(Delimiter)
    module.setOption(ResolversKey, v) // TODO remove in 2018.3
    getState(module).resolvers
  }

  /** The build module for given id/uri in project. */
  def findBuildModule(project: Project, id: String, uri: URI): Option[Module] = {
    val moma = ModuleManager.getInstance(project)

    moma.getModules.find { m =>
      val state = getState(m)
      state.buildForId == id && new URI(state.buildForURI) == uri
    }
  }

  def setBuildForModule(module: Module, id: String, uri: URI): Unit = {
    val state = getState(module)
    state.buildForId = id
    state.buildForURI = uri.toString
  }


  // substitution of dollars is necessary because IDEA will interpret a string in the form of $something$ as a path variable
  // and warn the user of "undefined path variables" (SCL-10691)
  val substitutePrefix = "SUB:"
  val substituteDollar = "DOLLAR"
  def substituteOptionString(raw: String): String =
    raw
      .replace(substitutePrefix, substitutePrefix+substitutePrefix)
      .replace("$",substitutePrefix+substituteDollar)

  def unsubstituteOptionString(substituted: String): String =
    substituted
      .replace(substitutePrefix+substitutePrefix, substitutePrefix)
      .replace(substitutePrefix+substituteDollar,"$")

  @State(
    name = "SbtModule",
    storages = Array(new Storage(StoragePathMacros.MODULE_FILE))
  )
  class ModuleState extends PersistentStateComponent[ModuleState] {

    var imports: String = ""
    var resolvers: String = ""
    var buildForId: String = ""
    var buildForURI: String = ""

    override def getState: ModuleState = this
    override def loadState(state: ModuleState): Unit = {
      imports = state.imports
      resolvers = state.resolvers
      buildForId = state.buildForId
      buildForURI = state.buildForURI
    }
  }

}
