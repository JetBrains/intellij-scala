import scala.annotation.tailrec

case class Tape[T](left: List[T], cell: T, right: List[T]) {
  def isZero: Boolean = ???
  def execute(ch: Char): Tape[T] = ???
}

class Brainfuck[T](func:Func[T]) {

  def execute(p: String) {
    val prog = p.replaceAll("[^\\+\\-\\[\\]\\.\\,\\>\\<]", "")

    @tailrec def braceMatcher(pos: Int, stack: List[Int], o2c: Map[Int, Int]): Map[Int,Int] =
      if(pos == prog.length) o2c else prog(pos) match {
        case '[' => braceMatcher(pos + 1, pos :: stack, o2c)
        case ']' => braceMatcher(pos + 1, stack.tail, o2c + (stack.head -> pos))
        case _ => braceMatcher(pos + 1, stack, o2c)
      }

    val open2close = braceMatcher(0, Nil, Map())
    val close2open = open2close.map(it => it.swap)

    @tailrec def ex(pos:Int, tape:Tape[T]): Unit =
      if(pos < prog.length) ex(prog(pos) match {
        case '[' if tape.isZero => open2close(pos)
        case ']' if ! tape.isZero => close2open(pos)
        case _ => pos + 1
      }, tape.execute(prog(pos)))

    println("---running---")
    println("\n---done---")
  }
}