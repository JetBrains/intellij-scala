// Contributed by Daniel Gronau
import scala.annotation._

trait Func[T] {
  val zero: T
  def inc(t: T): T
  def dec(t: T): T
  def in: T
  def out(t: T): Unit
}

object ByteFunc extends Func[Byte] {
  override val zero: Byte = 0
  override def inc(t: Byte) = ((t + 1) & 0xFF).toByte
  override def dec(t: Byte) = ((t - 1) & 0xFF).toByte
  override def in: Byte = scala.io.StdIn.readByte
  override def out(t: Byte) { print(t.toChar) }
}

case class Tape[T](left: List[T], cell: T, right: List[T])(
  implicit func: Func[T]
) {
  private def headOf(list: List[T]) = if (list.isEmpty) func.zero else list.head
  private def tailOf(list: List[T]) = if (list.isEmpty) Nil else list.tail
  def isZero: Boolean = cell == func.zero
  def execute(ch: Char): Tape[T] = ch match {
    case '+'       => copy(cell = func.inc(cell))
    case '-'       => copy(cell = func.dec(cell))
    case '<'       => Tape(tailOf(left), headOf(left), cell :: right)
    case '>'       => Tape(cell :: left, headOf(right), tailOf(right))
    case '.'       => func.out(cell); this
    case ','       => copy(cell = func.in)
    case '[' | ']' => this
    case _         => sys.error("Unexpected token: " + ch)
  }
}

object Tape {
  def empty[T](func: Func[T]): Tape[T] = Tape(Nil, func.zero, Nil)(func)
}

class Brainfuck[T](func: Func[T]) {

  private var prog: String = _
  private var open2close: Map[Int, Int] = _
  private var close2open: Map[Int, Int] = _

  @tailrec private def braceMatcher(pos: Int,
                                    stack: List[Int],
                                    o2c: Map[Int, Int]): Map[Int, Int] =
    if (pos == prog.length) o2c
    else
      prog(pos) match {
        case '[' => braceMatcher(pos + 1, pos :: stack, o2c)
        case ']' =>
          braceMatcher(pos + 1, stack.tail, o2c + (stack.head -> pos))
        case _ => braceMatcher(pos + 1, stack, o2c)
      }

  @tailrec private def ex(pos: Int, tape: Tape[T]): Unit =
    if (pos < prog.length) ex(prog(pos) match {
      case '[' if tape.isZero  => open2close(pos)
      case ']' if !tape.isZero => close2open(pos)
      case _                   => pos + 1
    }, tape.execute(prog(pos)))

  def execute(p: String) {
    prog = p.replaceAll("[^\\+\\-\\[\\]\\.\\,\\>\\<]", "")
    open2close = braceMatcher(0, Nil, Map())
    close2open = open2close.map(it => it.swap)

    println("---running---")
    ex(0, Tape.empty(func))
    println("\n---done---")
  }
}
