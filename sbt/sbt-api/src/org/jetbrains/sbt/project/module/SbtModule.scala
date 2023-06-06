package org.jetbrains.sbt.project.module

import com.intellij.openapi.components._
import com.intellij.openapi.module.Module
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.resolvers.{SbtIvyResolver, SbtMavenResolver, SbtResolver}

import java.net.URI
import java.util.regex.Pattern
import scala.beans.BeanProperty
import scala.jdk.CollectionConverters._

object SbtModule {

  private def getState(module: Module): SbtModuleState =
    module.getService(classOf[SbtModule]).getState

  object Build {

    def apply(module: Module): URI =
      new URI(getState(module).buildForURI)

    def update(module: Module, uri: URI): Unit =
      getState(module).buildForURI = uri.toString
  }

  object Imports {

    private val ImportsDelimiter = ", "

    // substitution of dollars is necessary because IDEA will interpret a string in the form of $something$ as a path variable
    // and warn the user of "undefined path variables" (SCL-10691)
    private val SubstitutePrefix = "SUB:"
    private val SubstituteDollar = "DOLLAR"

    private def encode(text: String): String = {
      text
        .replace(SubstitutePrefix, SubstitutePrefix + SubstitutePrefix)
        .replace("$", SubstitutePrefix + SubstituteDollar)
    }

    private def decode(text: String): String =
      text
        .replace(SubstitutePrefix + SubstitutePrefix, SubstitutePrefix)
        .replace(SubstitutePrefix + SubstituteDollar, "$")

    def apply(module: Module): Seq[String] = {
      val state = getState(module)
      val importsStrOpt = Option(state.imports).filter(_.nonEmpty)
      importsStrOpt.fold(Sbt.DefaultImplicitImports)(deserializeSeq)
    }

    def update(module: Module, imports: java.util.List[String]): Unit = {
      val newImports = serializeSeq(imports.asScala.toSeq)
      getState(module).imports = newImports
    }

    private def serializeSeq(imports: Seq[String]): String = {
      val concatenated = imports.mkString(ImportsDelimiter)
      val encoded = encode(concatenated)
      encoded
    }

    private def deserializeSeq(string: String): Seq[String] = {
      val decoded = decode(string)
      val parts = decoded.split(ImportsDelimiter)
      parts.toSeq
    }
  }

  object Resolvers {

    def apply(module: Module): Set[SbtResolver] = {
      val state = getState(module)
      val resolversStrOpt = Option(state.resolvers).filter(_.nonEmpty)
      resolversStrOpt.toSet.flatMap(deserializeSet)
    }

    def update(module: Module, resolvers: Set[SbtResolver]): Unit = {
      val serialized = serializeSet(resolvers)
      getState(module).resolvers = serialized
    }

    private val ResolversDelimiter = ", "
    private val ResolverFieldsDelimiter = "|"
    private val ResolverFieldsDelimiterEncoded = "&delim;"
    private val ResolverFieldsDelimiterPattern = Pattern.quote(ResolverFieldsDelimiter)

    private def encodeName(name: String): String = name.replace(ResolverFieldsDelimiter, ResolverFieldsDelimiterEncoded)
    private def decodeName(name: String): String = name.replace(ResolverFieldsDelimiterEncoded, ResolverFieldsDelimiter)

    private def serialize(resolver: SbtResolver): String = {
      val root = resolver.root
      val name = encodeName(resolver.name)
      val parts = resolver match {
        case _: SbtMavenResolver => Seq(root, "maven", name)
        case ir: SbtIvyResolver => Seq(root, "ivy", ir.isLocal, name)
      }
      parts.mkString(ResolverFieldsDelimiter)
    }

    private def deserialize(resolverStr: String): Option[SbtResolver] = {
      val parts = resolverStr.split(ResolverFieldsDelimiterPattern).toSeq
      parts match {
        case Seq(root, "maven", name)        => Some(new SbtMavenResolver(decodeName(name), root))
        case Seq(root, "ivy", isLocal, name) => Some(new SbtIvyResolver(decodeName(name), root, isLocal = isLocal.toBooleanOption.getOrElse(false)))
        case _                               => None
      }
    }

    private def serializeSet(resolvers: Set[SbtResolver]): String =
      resolvers.map(serialize).mkString(ResolversDelimiter)

    private def deserializeSet(string: String): Set[SbtResolver] = {
      val resolvers = string.split(ResolversDelimiter)
      resolvers.flatMap(deserialize).toSet
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
