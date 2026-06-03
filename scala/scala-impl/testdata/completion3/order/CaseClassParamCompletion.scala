
abstract case class BigCase()
case class GoodSportyStudent(aname: String, asurName: List[String], aimark: Int, sporta: Seq[Int]) extends BigCase

def test(b: BigCase): Unit = {
  b match {
    case GoodSportyStudent(<caret>
      }
      }