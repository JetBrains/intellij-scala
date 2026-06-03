// Notification message: Removed 2 imports
import scala.collection.immutable.{
  AbstractSeq, List, Seq, StringOps,
  StringView
}
import scala.util.Random

object Main {
  def main(args: Array[String]): Unit = {
    val x1: Seq[_] = ???
    val x2: List[_] = ???
    val x6: AbstractSeq[_] = ???
    val x3: StringOps = ???
    val x5: StringView = ???
  }

  def localImports(): Unit = {

    import scala.util.Random
    import scala.collection.immutable.{
      AbstractSeq, List, Seq, StringOps,
      StringView
    }

    val x1: Seq[_] = ???
    val x2: List[_] = ???
    val x6: AbstractSeq[_] = ???
    val x3: StringOps = ???
    val x5: StringView = ???
  }
}
/*
import scala.collection.immutable.{
  AbstractSeq, List, Seq, StringOps,
  StringView
}

object Main {
  def main(args: Array[String]): Unit = {
    val x1: Seq[_] = ???
    val x2: List[_] = ???
    val x6: AbstractSeq[_] = ???
    val x3: StringOps = ???
    val x5: StringView = ???
  }

  def localImports(): Unit = {

    import scala.collection.immutable.{
      AbstractSeq, List, Seq, StringOps,
      StringView
    }

    val x1: Seq[_] = ???
    val x2: List[_] = ???
    val x6: AbstractSeq[_] = ???
    val x3: StringOps = ???
    val x5: StringView = ???
  }
}
*/