import scala.reflect.ClassTag

trait Graph[N, E[X] <: EdgeLike[X]]

trait EdgeLike[+N]

object GraphComponents {
  implicit def graphToComponents[N: ClassTag, E[X] <: EdgeLike[X]](g: Graph[N, E]): GraphComponents[N, E] = ???
}

class GraphComponents[N, E[X] <: EdgeLike[X]](val g: Graph[N, E])(implicit edgeT: ClassTag[N]) {
  def gcMethod(): String = ???
}

object Test {

  val g: Graph[Char, EdgeLike] = ???

  g.<ref>gcMethod()
}