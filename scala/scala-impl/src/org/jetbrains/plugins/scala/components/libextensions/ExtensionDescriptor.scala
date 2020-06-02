package org.jetbrains.plugins.scala.components.libextensions

import org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier.Version

import scala.collection.mutable.ArrayBuffer
import scala.language.postfixOps
import scala.xml.{Elem, XML}

@SerialVersionUID(1L)
case class ExtensionDescriptor(interface: String, impl: String, name: String, description: String, pluginId: String)

@SerialVersionUID(1L)
case class IdeaVersionDescriptor(sinceBuild: Version,
                                 untilBuild: Version,
                                 pluginId: Option[String],
                                 defaultPackage: String,
                                 extensions: Seq[ExtensionDescriptor] = Nil)
@SerialVersionUID(1L)
case class LibraryDescriptor(name: String,
                             id: String,
                             description: String,
                             vendor: String,
                             version: String,
                             pluginVersions: Seq[IdeaVersionDescriptor])


object LibraryDescriptor {
  def parse(str: String): Either[String, LibraryDescriptor] = parse(XML.loadString(str))
  def parse(data: Elem):  Either[String, LibraryDescriptor] = {
    import scala.xml._

    def verifyDescriptor(libraryDescriptor: LibraryDescriptor): Seq[String] = {
      val errors = ArrayBuffer[String]()
      if (libraryDescriptor.name.isEmpty) errors += "Descriptor name is empty"
      if (libraryDescriptor.pluginVersions.isEmpty) errors += "Descriptor version is empty"

      errors
    }

    def parseExtension(node: Node): ExtensionDescriptor = {
      val interface     = node \@ "interface"
      val impl          = node \@ "implementation"
      val pluginId      = node \@ "pluginId"
      val name          = node \ "name" text
      val description   = node \ "description" text

      ExtensionDescriptor(interface, impl, name, description, pluginId)
    }

    def parseIdeaVersionDescriptor(node: Node): IdeaVersionDescriptor = {
      val sinceBuild = Option(node \@ "since-build").filter(_.nonEmpty).flatMap(Version.parse).getOrElse(Version.Zero)
      val untilBuild = Option(node \@ "until-build").filter(_.nonEmpty).flatMap(Version.parse).getOrElse(Version.Snapshot)
      val pluginId   = Option(node \@ "pluginId").filter(_.nonEmpty)
      val defaultPackage   = node  \@ "defaultPackage"
      val extensions        = node \  "extension" map parseExtension

      IdeaVersionDescriptor(sinceBuild, untilBuild, pluginId, defaultPackage, extensions)
    }

    val name    = data \ "name" text
    val id      = data \ "id" text
    val descr   = data \ "description" text
    val vendor  = data \ "vendor" text
    val version = data \ "version" text

    val descriptors = data \ "ideaVersion" map parseIdeaVersionDescriptor

    val descriptor = LibraryDescriptor(name, id, descr, vendor, version, descriptors)

    verifyDescriptor(descriptor) match {
      case Seq(errors)  => Left(errors)
      case Nil          => Right(descriptor)
    }
  }
}