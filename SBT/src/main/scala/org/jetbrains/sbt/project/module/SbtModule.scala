package org.jetbrains.sbt
package project.module

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Key
import org.jetbrains.sbt.project.structure.Resolver
import org.jetbrains.sbt.resolvers.SbtResolver

/**
 * @author Pavel Fatin
 */
object SbtModule {
  private val ImportsKey = "sbt.imports"

  private val Delimiter = ", "

  private val ResolversKey: Key[Set[SbtResolver]] = new Key("sbt.resolvers")

  def getImportsFrom(module: Module): Seq[String] =
    Option(module.getOptionValue(ImportsKey)).fold(Sbt.DefaultImplicitImports)(_.split(Delimiter))

  def setImportsTo(module: Module, imports: Seq[String]) =
    module.setOption(ImportsKey, imports.mkString(Delimiter))

  def getResolversFrom(module: Module): Set[SbtResolver] =
    Option(module.getUserData(ResolversKey)).getOrElse(Set.empty)

  def setResolversTo(module: Module, resolvers: Set[SbtResolver]) =
    module.putUserData(ResolversKey, resolvers)
}
