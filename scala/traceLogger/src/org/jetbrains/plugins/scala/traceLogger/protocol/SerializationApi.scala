package org.jetbrains.plugins.scala.traceLogger.protocol

object SerializationApi extends upickle.AttributeTagged {
  override val tagName: String = "type"

  // make Some be serialized directly and None serialized as null
  override implicit def OptionWriter[T: Writer]: Writer[Option[T]] =
    implicitly[Writer[T]].comap[Option[T]] {
      case None => null.asInstanceOf[T]
      case Some(x) => x
    }

  override implicit def OptionReader[T: Reader]: Reader[Option[T]] = {
    new Reader.Delegate[Any, Option[T]](implicitly[Reader[T]].map(Some(_))){
      override def visitNull(index: Int): Option[Nothing] = None
    }
  }
}