import scala.language.experimental.namedTypeArguments

def combine[A](first: A)[B](second: B)(fallback: A): B = second

def test(): Unit = {
  comb<caret>ine[Int](1)[String](second = "text")(1)
}
