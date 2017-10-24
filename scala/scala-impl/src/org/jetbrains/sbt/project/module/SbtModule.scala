package org.jetbrains.sbt
package project.module

import com.intellij.openapi.module.Module
import org.jetbrains.sbt.resolvers.SbtResolver

/**
 * @author Pavel Fatin
 */
object SbtModule {
  private val ImportsKey = "sbt.imports"

  private val Delimiter = ", "

  private val ResolversKey = "sbt.resolvers"

  def getImportsFrom(module: Module): Seq[String] =
    Option(module.getOptionValue(ImportsKey))
      .filter(_.nonEmpty)
      .map(v => unsubstituteOptionString(v).split(Delimiter).toSeq)
      .getOrElse(Sbt.DefaultImplicitImports)

  def setImportsTo(module: Module, imports: Seq[String]): Unit = {
    val v = substituteOptionString(imports.mkString(Delimiter))
    module.setOption(ImportsKey, v)
  }

  def getResolversFrom(module: Module): Set[SbtResolver] =
    Option(module.getOptionValue(ResolversKey)).map { str =>
      str.split(Delimiter).map(SbtResolver.fromString).collect {
        case Some(r) => r
      }.toSet
    }.getOrElse(Set.empty)

  def setResolversTo(module: Module, resolvers: Set[SbtResolver]) =
    Option(module.setOption(ResolversKey, resolvers.map(_.toString).mkString(Delimiter)))

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
}
