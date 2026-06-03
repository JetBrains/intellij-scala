package org.jetbrains.plugins.scala.externalLibraries.scio

import org.jetbrains.plugins.scala.externalLibraries.scio.ScioInjector._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector

/**
  * Support for https://github.com/spotify/scio
  *
  * Handles @AvroType.toSchema/fromSchema/fromSchemaFile macro annotations
  */
class ScioInjector extends SyntheticMembersInjector {
  override def injectSupers(source: ScTypeDefinition): Seq[String] =
    source match {
      case clazz: ScClass if hasShemaAnnotation(clazz) =>
        Seq(HasAvroAnnotationFqn)
      case _ => Nil
    }
}

object ScioInjector {
  private val HasAvroAnnotationFqn = "com.spotify.scio.avro.types.AvroType.HasAvroAnnotation"

  private val ToSchemaFqn = "com.spotify.scio.avro.types.AvroType.toSchema"
  private val FromSchemaFqn = "com.spotify.scio.avro.types.AvroType.fromSchema"
  private val FromSchemaFileFqn = "com.spotify.scio.avro.types.AvroType.fromSchemaFile"

  private def hasShemaAnnotation(source: ScTypeDefinition): Boolean =
    (source.findAnnotationNoAliases(ToSchemaFqn) != null) ||
      (source.findAnnotationNoAliases(FromSchemaFqn) != null) ||
      (source.findAnnotationNoAliases(FromSchemaFileFqn) != null)
}