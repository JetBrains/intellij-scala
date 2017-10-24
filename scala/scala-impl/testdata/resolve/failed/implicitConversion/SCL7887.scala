import scala.collection.Map

object SCL7887 {
  final implicit class MapUnapply[K, V](val self: Map[K, V]) extends AnyVal {
    @inline
    def extract = new MapUnapply.Extractor(self)
  }

  object MapUnapply {
    final class Extractor[K, V] private[MapUnapply](val self: Map[K, V]) extends AnyVal {
      @inline
      def unapply(key: K): Option[V] = self get key
    }
  }

  def apply() = {
    val testMap = Map("one" -> 1, "two" -> 2)
    "one" match {
      case testMap.<ref>extract(n) => n
      case _ => -1
    }
  }
}