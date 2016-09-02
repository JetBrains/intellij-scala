object Main {
  import scala.collection.GenTraversable
  import scala.collection.generic.GenericTraversableTemplate
  import scala.language.{higherKinds, implicitConversions, reflectiveCalls}

  implicit def pimpIterable[CC[X] <: GenTraversable[X], T](xs: GenericTraversableTemplate[T, CC]): {def test: Int} =
    new {def test = 0}

  List(1).<ref>test
}
