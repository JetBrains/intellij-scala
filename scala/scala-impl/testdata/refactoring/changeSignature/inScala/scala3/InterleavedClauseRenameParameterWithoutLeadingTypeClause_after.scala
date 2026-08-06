def combine(first: Int)[B](renamedSecond: B)(fallback: Int): B = renamedSecond

def test(): Unit = {
  combine(1)[String](renamedSecond = "text")(1)
}
