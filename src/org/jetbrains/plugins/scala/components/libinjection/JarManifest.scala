package org.jetbrains.plugins.scala.components.libinjection

/**
  * Created by mucianm on 10.02.16.
  */

import java.io.File

import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile}
import org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier.Version

import scala.xml._

class InvalidManifest(where: Node, expected: String)
  extends Exception(s"Malformed library manifest at '$where', expected $expected")

// TODO: allow user to provide compiler settings for sources set
case class InjectorDescriptor(version: Int, iface: String, impl: String, sources: Seq[String])
case class PluginDescriptor(since: Version, until: Version, injectors: Seq[InjectorDescriptor])
case class JarManifest(pluginDescriptors: Seq[PluginDescriptor], jarPath: String, modTimeStamp: Long) {
  def serialize() = {
    <intellij-compat> {
        for (PluginDescriptor(since, until, injtors) <- pluginDescriptors) {
          <scala-plugin since-version={since.toString} until-version={until.toString}> {
            for (InjectorDescriptor(version, iface, impl, srcs) <- injtors) {
              <psi-injector version={version.toString} ifnterface={iface} implementation={impl}> {
                  for (src <- srcs) <source>{src}</source>
                }
              </psi-injector>
            }
          }
          </scala-plugin>
        }
      }
    </intellij-compat>
  }
}

object JarManifest {
  def deserialize(f: VirtualFile, containingJar: VirtualFile = null): JarManifest = {
    if (containingJar == null)
      deserialize(XML.load(f.getInputStream), VfsUtilCore.getVirtualFileForJar(f))
    else
      deserialize(XML.load(f.getInputStream), containingJar)
  }

  def deserialize(elem: Elem, containingJar: VirtualFile): JarManifest = {
    def buildInjectorDescriptor(n: Node): InjectorDescriptor = {
      val version = (n \ "@version").headOption.map(_.text.toInt).getOrElse(0)
      val iface   = (n \ "@interface").headOption.map(_.text).getOrElse(throw new InvalidManifest(n, "interface"))
      val impl    = (n \ "@implementation").headOption.map(_.text).getOrElse(throw new InvalidManifest(n, "implementation"))
      val sources = (n \\ "source").map(_.text)
      InjectorDescriptor(version, iface, impl, sources)
    }
    def buildPluginDescriptor(n: Node): PluginDescriptor = {
      val since = Version.parse((n \ "@since-version").text).getOrElse(throw new InvalidManifest(n, "since-version"))
      val until = Version.parse((n \ "@until-version").text).getOrElse(throw new InvalidManifest(n, "until-version"))
      val injectors = (n \\ "psi-injector").map(buildInjectorDescriptor)
      PluginDescriptor(since, until, injectors)
    }
    elem \\ "intellij-compat" match {
      case NodeSeq.Empty => throw new InvalidManifest(elem, "<intellij-compat> with plugin descriptors")
      case xss: NodeSeq =>
        JarManifest((xss \\ "scala-plugin").map(buildPluginDescriptor),
          containingJar.getPath.replaceAll("!/", ""),
          new File(containingJar.getPath.replaceAll("!/", "")).lastModified())
    }
  }
}