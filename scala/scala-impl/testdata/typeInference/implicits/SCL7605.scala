import scala.language.implicitConversions

object Password {
  val CHARS = "!@#$%^&*()"

  def main(args: Array[String]): Unit = {
    val numOfCases = readInt()
    (1 to numOfCases) foreach(x => solveTestCase(x))
  }

  def solveTestCase(num : Int ): Unit ={
    readLine()
    implicit val line = readLine()
    val score : Int = /*start*/oneDigit + oneLatin + oneChar + six + ten + smallCapital + identical/*end*/

    println(s"Case #$num:")

    println(score match {
      case 0|1|2|3 => "weak"
      case 4 | 5 => "normal"
      case _ => "strong"
    })
  }

  implicit def checkAgainst( bool : Boolean) : Int = if(bool) 1 else 0

  def oneDigit(implicit pass : String) = pass exists { cha => (cha >= '0') && (cha <= '9')}

  def oneLatin(implicit pass : String) = pass.toLowerCase.exists{ cha => (cha >= 'a' && cha <= 'z')}

  def oneChar(implicit pass : String) = pass exists { p => CHARS.contains(p)}

  def six (implicit pass : String) = pass.size >= 6

  def ten (implicit pass : String ) = pass.size > 10

  def smallCapital (implicit pass : String) = !(pass.toLowerCase.equals(pass) || pass.toUpperCase.equals(pass))

  def identical (implicit pass :  String )= {
    var bool = true
    pass.foreach { ch: Char => if (pass.count(cha  => cha.equals(ch)) != 1) bool =  false }
    bool
  }
}

//Int