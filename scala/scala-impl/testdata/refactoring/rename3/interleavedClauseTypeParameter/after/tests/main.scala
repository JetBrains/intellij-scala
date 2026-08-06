package tests

import scala.language.experimental.namedTypeArguments

def combine[A](first: A)[NameAfterRename](second: NameAfterRename): NameAfterRename = second

def test(): Unit = {
  combine[Int](1)[NameAfterRename = String]("text")
}
