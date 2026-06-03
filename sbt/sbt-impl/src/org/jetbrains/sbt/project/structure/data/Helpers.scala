package org.jetbrains.sbt.project.structure.data

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.{EelNioBridgeServiceKt, LocalEelDescriptor}
import com.intellij.platform.eel.provider.utils.EelPathUtils
import org.jetbrains.plugins.scala.extensions.PathExt

import java.net.URI
import java.nio.file.Paths
import scala.xml.Node

private object Helpers:

  extension (node: Node)
    infix def !(name: String): Node = node \ name match
      case Seq() => throw new RuntimeException("None of " + name + " nodes is found in " + node)
      case Seq(child) => child
      case _ => throw new RuntimeException("Multiple " + name + " nodes are found in " + node)

  extension (str: String)
    def uri(using descriptor: EelDescriptor): URI = canonUri(new URI(str.replace("\\", "/"))) // handle windows separators

  private def canonUri(uri: URI)(using descriptor: EelDescriptor): URI =
    val uri1 =
      if (uri.getScheme == "file") {
        val path =
          if descriptor == LocalEelDescriptor.INSTANCE then
            Paths.get(uri).toCanonicalPath
          else
            // I am not 100% sure if this conversion is required, maybe we could just use uri.getPath within eel.
            // From what I have checked, it is not harmful.
            val eelPath = EelPath.parse(uri.getPath, descriptor).normalize()
            EelNioBridgeServiceKt.asNioPath(eelPath)

        EelPathUtils.getUriLocalToEel(path)
      } else uri
    uri1.normalize()
