package org.jetbrains.plugins.scala.components.libinjection

/**
  * Created by mucianm on 10.02.16.
  */

import org.jetbrains.plugins.scala.components.ScalaPluginVersionVerifier.Version

import scala.collection.immutable.Seq
import scala.xml._

class InvalidManifest(where: Node, expected: String)
  extends Exception(s"Malformed library manifest at '$where', expected $expected")

case class InjectorDescriptor(version: Int, iface: String, impl: String, sources: Seq[String])
case class PluginDescriptor(since: Version, until: Version, injectors: Seq[InjectorDescriptor])
case class LibraryManifest(pluginDescriptors: Seq[PluginDescriptor]) {
  def serialize() = {
    <intellij-compat> {
        for (PluginDescriptor(since, until, injtors) <- pluginDescriptors) {
          <scala-plugin since-version={since} until-version={until}> {
            for (InjectorDescriptor(version, iface, impl, srcs) <- injtors) {
              <psi-injector version={version} ifnterface={iface} implementation={impl}> {
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

object LibraryManifest {
  def deserialize(elem: Elem): LibraryManifest = {
    def buildInjectorDescriptor(n: Node): InjectorDescriptor = {
      val version = (n \ "@version").headOption.map(_.text.toInt).getOrElse(throw new InvalidManifest(n, "version"))
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
    elem match {
      case <intellij-compat>{nodes}</intellij-compat> =>
        LibraryManifest(nodes.map(buildPluginDescriptor))
      case _ => throw new InvalidManifest(elem, "<intellij-compat> with plugin descriptors")
    }
  }
}