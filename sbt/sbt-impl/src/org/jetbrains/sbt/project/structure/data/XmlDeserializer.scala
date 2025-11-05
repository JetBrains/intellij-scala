package org.jetbrains.sbt.project.structure.data

import scala.reflect.ClassTag
import scala.xml.{Node, NodeSeq}

trait XmlDeserializer[T]:
  def deserialize(what: Node): Either[Throwable, T]

object XmlDeserializer:
  extension (node: Node)
    def deserialize[T](using deserializer: XmlDeserializer[T]): Either[Throwable, T] =
      deserializer.deserialize(node)
  end extension

  extension (nodeSeq: NodeSeq)
    def deserializeNodeSeq[T](using deserializer: XmlDeserializer[T]): Seq[T] =
      nodeSeq.flatMap(_.deserialize[T].fold(_ => None, Some.apply))

    def deserializeOne[T](using deserializer: XmlDeserializer[T], manifest: ClassTag[T]): Either[Throwable, T] =
      val ts = nodeSeq.map(_.deserialize[T]).collect { case Right(t) => t }
      if ts.isEmpty then
        Left(new Error("None of " + manifest.runtimeClass.getSimpleName + " is found in " + nodeSeq))
      else if ts.length > 1 then
        Left(new Error("Multiple instances of " + manifest.runtimeClass.getSimpleName + " are found in " + nodeSeq))
      else
        Right(ts.head)
  end extension
