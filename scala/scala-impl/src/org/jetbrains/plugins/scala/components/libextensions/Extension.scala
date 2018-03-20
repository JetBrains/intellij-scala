package org.jetbrains.plugins.scala.components.libextensions

import org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier.Version

import scala.collection.mutable.ArrayBuffer
import scala.language.postfixOps

case class Extension(fqn: String, description: String, enabled: Boolean)

case class PluginDescriptor(sinceBuild: Version,
                            untilBuild: Version,
                            classPath: Option[String] = None,
                            typeTransformers: Seq[Extension] = Nil,
                            exprTransformers: Seq[Extension] = Nil,
                            templDefTransformers: Seq[Extension] = Nil,
                            syntheticMemberInjectors: Seq[Extension] = Nil,
                            inspections: Seq[Extension] = Nil,
                            intentions: Seq[Extension] = Nil,
                            migrators: Seq[Extension] = Nil) {

  def flattenExtensions(): Seq[Extension] =
    typeTransformers ++
      exprTransformers ++
      templDefTransformers ++
      syntheticMemberInjectors ++
      inspections ++
      intentions ++
      migrators
}

case class LibraryDescriptor(name: String,
                             id: String,
                             description: String,
                             vendor: String,
                             version: String,
                             pluginVersions: Seq[PluginDescriptor]) {

  def getCurrentPluginDescriptor: Option[PluginDescriptor] = pluginVersions.headOption // TODO

}



object LibraryDescriptor {
  def parse(data: String): Either[String, LibraryDescriptor] = {
    import scala.xml._

    def verifyDescriptor(libraryDescriptor: LibraryDescriptor): Seq[String] = {
      val errors = ArrayBuffer[String]()
      if (libraryDescriptor.name.isEmpty) errors += "Descriptor name is empty"
      if (libraryDescriptor.pluginVersions.isEmpty) errors += "Descriptor version is empty"

      errors
    }

    def parseExtension(node: Node): Extension = {
      val fqn     = node \@ "implementation"
      val enabled = Option(node \@ "enabled").forall(_.toBoolean)
      val description = node \ "description" text

      Extension(fqn, description, enabled)
    }

    def parrseOluginDescriptor(node: Node): PluginDescriptor = {
      val sinceBuild = Option(node \@ "since-build").filter(_.nonEmpty).flatMap(Version.parse).orNull
      val untilBuild = Option(node \@ "until-build").filter(_.nonEmpty).flatMap(Version.parse).orNull
      val classPath  = Option(node \@ "classpath").filter(_.nonEmpty)

      val typeTransformers = node \ "type-transformer" map parseExtension
      val exprTransformers = node \ "expression-transformer" map parseExtension
      val templDefTransformers = node \ "templatedef-transformer" map parseExtension
      val syntheticMemberInjectors = node \ "synthetic-member-injector" map parseExtension
      val inspections = node \ "inspection" map parseExtension
      val intentions = node \ "intention" map parseExtension
      val migrators = node \ "migrator" map parseExtension

      PluginDescriptor(sinceBuild, untilBuild, classPath, typeTransformers, exprTransformers, templDefTransformers,
        syntheticMemberInjectors, inspections, intentions, migrators)
    }

    val xml = XML.loadString(data)
    val name    = xml \ "name" text
    val id      = xml \ "id" text
    val descr   = xml \ "description" text
    val vendor  = xml \ "vendor" text
    val version = xml \ "description" text

    val descriptors = xml \ "scala-plugin" map parrseOluginDescriptor

    val descriptor = LibraryDescriptor(name, id, descr, vendor, version, descriptors)

    verifyDescriptor(descriptor) match {
      case Seq(errors)  => Left(errors)
      case Nil          => Right(descriptor)
    }
  }
}