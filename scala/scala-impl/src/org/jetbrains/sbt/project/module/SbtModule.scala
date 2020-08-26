package org.jetbrains.sbt
package project.module

import java.net.URI

import com.intellij.openapi.components._
import com.intellij.openapi.module.Module
import org.jetbrains.annotations.NonNls
import org.jetbrains.sbt.resolvers.SbtResolver

import scala.beans.BeanProperty
import scala.jdk.CollectionConverters._

/**
  * @author Pavel Fatin
  */
object SbtModule {

  // substitution of dollars is necessary because IDEA will interpret a string in the form of $something$ as a path variable
  // and warn the user of "undefined path variables" (SCL-10691)
  @NonNls private val SubstitutePrefix = "SUB:"
  @NonNls private val SubstituteDollar = "DOLLAR"

  @Deprecated
  @NonNls private val ImportsKey = "sbt.imports"

  @Deprecated
  @NonNls private val Delimiter = ", "

  @Deprecated
  @NonNls private val ResolversKey = "sbt.resolvers"

  private def getState(module: Module): SbtModuleState =
    module.getService(classOf[SbtModule]).getState

  object Build {

    def apply(module: Module): URI =
      new URI(getState(module).buildForURI)

    def update(module: Module, uri: URI): Unit = {
      getState(module).buildForURI = uri.toString
    }
  }

  object Imports {

    def apply(module: Module): Seq[String] =
      Option(getState(module).imports)
        .filter(_.nonEmpty)
        .orElse(Option(module.getOptionValue(ImportsKey))) // TODO remove in 2018.3+
        .filter(_.nonEmpty)
        .fold(Sbt.DefaultImplicitImports) { implicitImports =>
          implicitImports
            .replace(SubstitutePrefix + SubstitutePrefix, SubstitutePrefix)
            .replace(SubstitutePrefix + SubstituteDollar, "$")
            .split(Delimiter)
            .toSeq
        }

    def update(module: Module, imports: java.util.List[String]): Unit = {
      val newImports = imports.asScala.mkString(Delimiter)
        .replace(SubstitutePrefix, SubstitutePrefix + SubstitutePrefix)
        .replace("$", SubstitutePrefix + SubstituteDollar)

      module.setOption(ImportsKey, newImports) // TODO remove in 2018.3+

      getState(module).imports = newImports
    }
  }

  object Resolvers {

    def apply(module: Module): Set[SbtResolver] =
      Option(getState(module).resolvers)
        .filter(_.nonEmpty)
        .orElse(Option(module.getOptionValue(ResolversKey))) // TODO remove in 2018.3+
        .fold(Set.empty[SbtResolver]) { str =>
        str.split(Delimiter)
          .flatMap(SbtResolver.fromString)
          .toSet
      }

    def update(module: Module, resolvers: collection.Set[SbtResolver]): Unit = {
      val newResolvers = resolvers.map(_.toString)
        .mkString(Delimiter)

      module.setOption(ResolversKey, newResolvers) // TODO remove in 2018.3

      getState(module).resolvers
    }
  }
}

@State(
  name = "SbtModule",
  storages = Array(new Storage(value = StoragePathMacros.MODULE_FILE, roamingType = RoamingType.DISABLED))
)
final class SbtModule extends PersistentStateComponent[SbtModuleState] {

  @BeanProperty
  var myState: SbtModuleState = new SbtModuleState()

  override def getState: SbtModuleState = myState

  override def loadState(state: SbtModuleState): Unit = {
    myState = state
  }
}

class SbtModuleState {
  @BeanProperty
  var imports: String = ""
  @BeanProperty
  var resolvers: String = ""
  @BeanProperty
  var buildForURI: String = ""
}
