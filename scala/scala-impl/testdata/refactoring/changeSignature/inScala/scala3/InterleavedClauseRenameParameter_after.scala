import scala.language.experimental.namedTypeArguments

def combine[A](first: A)[B](renamedSecond: B)(fallback: A): B = renamedSecond

def test(): Unit = {
  combine[Int](1)[String](renamedSecond = "text")(1)
}
