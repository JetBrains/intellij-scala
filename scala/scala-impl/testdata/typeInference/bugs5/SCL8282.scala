class FirstElementTest extends FirstElement {
  implicit def findingNatureOfGenTraversable[E, TRAV[e] <: scala.collection.GenTraversable[e]]: FirstElementCollector[E, TRAV[E]] =
    new FirstElementCollector[E, TRAV[E]] {
      def firstElement(seq: TRAV[E]) = seq.head
    }
  println(/*start*/Seq(1).firstElement/*end*/)
}

trait FirstElement {
  import scala.language.higherKinds
  import scala.language.implicitConversions

  trait FirstElementCollector[E, C] {
    def firstElement(c: C): E
  }

  final class FirstElementWrapper[E, CTC[_]](collection: CTC[E])(implicit collector: FirstElementCollector[E, CTC[E]]) {
    def firstElement = collector.firstElement(collection)
  }

  implicit def convertToCollectionFirstElementWrapper[E, CTC[_]](collection: CTC[E])(implicit collecting: FirstElementCollector[E, CTC[E]]): FirstElementWrapper[E, CTC] = new FirstElementWrapper[E, CTC](collection)
}
//Int