def main(args: Array[String]) {
    val showbug = List(1,2,3)
    val notBuggy = showbug.foldLeft(0)(_ + _)// <- correct, plugin says "Int"
    val buggy = (0 /: showbug)(_ + _) // <- wrong, plugin says "Any"
    println(notBuggy)
    println(/*start*/buggy/*end*/)
  }
//Int