package tests

import scala.language.experimental.namedTypeArguments

def combine[A](first: A)[/*caret*/B](second: /*caret*/B): /*caret*/B = second

def test(): Unit = {
  combine[Int](1)[/*caret*/B = String]("text")
}
