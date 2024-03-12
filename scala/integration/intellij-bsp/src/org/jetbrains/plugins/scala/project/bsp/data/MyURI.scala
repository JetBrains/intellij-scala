package org.jetbrains.plugins.scala.project.bsp.data

import com.intellij.serialization.PropertyMapping

import java.net.URI
import java.util.Objects

@SerialVersionUID(2)
final class MyURI @PropertyMapping(Array("string"))(
  private val string: String
) extends Serializable {
  assert(string != null)

  @transient val uri: URI = new URI(string)

  def this(uri: URI) = {
    this(uri.toString)
  }

  override def toString: String = Objects.toString(uri)

  override def hashCode(): Int = Objects.hashCode(uri)

  override def equals(obj: Any): Boolean = obj match {
    case other: MyURI => uri == other.uri
    case _ => false
  }
}
