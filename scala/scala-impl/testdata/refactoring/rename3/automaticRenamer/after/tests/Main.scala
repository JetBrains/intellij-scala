class Bar extends Throwable

val bar: Bar = Bar()
val bar1: Bar = new Bar()

val bars: List[Bar] = List()
val foos1: Array[Bar] = Array()

@main def main(): Unit = {
  val bar: Bar = Bar()
  val someVerySpecialBar: Bar = Bar()
  val barAnother: Bar = new Bar()

  val anonymous = new Bar() {
  }

  val (bar1: Bar, bars: List[Bar]) = (Bar(), List[Bar]())

  try {
    for ((bar2: Bar) <- List[Bar]()) {

    }
  } catch {
    case bar: Bar =>
  }

  def local(bar: Bar): Unit = {

  }
}

def topLevel(bar: Bar): Unit = {

}

def collectionLikes(bars: List[Array[Bar]], foos2: Seq[Map[Bar, Bar]]): Unit = {

}

class BarImpl extends Bar()

object BarObj extends Bar()
