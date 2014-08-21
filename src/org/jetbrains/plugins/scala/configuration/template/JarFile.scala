package org.jetbrains.plugins.scala
package configuration.template

import java.io.{BufferedInputStream, File, IOException, InputStream}
import java.net.URL
import java.util.Properties

import org.jetbrains.jps.incremental.scala._

/**
 * @author Pavel Fatin
 */
class JarFile(resource: String, name: String) {
  def versionOf(file: File): Option[String] = JarFile.readProperty(file, resource, name)
}

object JarFile {
  val Library = new JarFile("library.properties", "version.number")

  val Compiler = new JarFile("compiler.properties", "version.number")

  val Reflect = new JarFile("reflect.properties", "version.number")

  def readProperty(file: File, resource: String, name: String): Option[String] = {
    try {
      val url = new URL("jar:%s!/%s".format(file.toURI.toString, resource))
      Option(url.openStream).flatMap(it => using(new BufferedInputStream(it))(readProperty(_, name)))
    } catch {
      case _: IOException => None
    }
  }

  private def readProperty(input: InputStream, name: String): Option[String] = {
    val properties = new Properties()
    properties.load(input)
    Option(properties.getProperty(name))
  }
}
