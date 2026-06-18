def combine(first: Int)[B](second: B)(fallback: Int): B = second

def test(): Unit = {
  comb<caret>ine(1)[String](second = "text")(1)
}
