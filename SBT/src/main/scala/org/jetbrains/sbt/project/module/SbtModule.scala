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
    Option(module.getOptionValue(ImportsKey)).fold(Sbt.DefaultImplicitImports)(_.split(Delimiter))

  def setImportsTo(module: Module, imports: Seq[String]) =
    module.setOption(ImportsKey, imports.mkString(Delimiter))

  def getResolversFrom(module: Module): Set[SbtResolver] =
    Option(module.getOptionValue(ResolversKey)).map { str =>
      str.split(Delimiter).map(SbtResolver.fromString).collect {
        case Some(r) => r
      }.toSet
    }.getOrElse(Set.empty)

  def setResolversTo(module: Module, resolvers: Set[SbtResolver]) =
    module.setOption(ResolversKey, resolvers.map(_.toString).mkString(Delimiter))
}
