package object accounting {
  sealed abstract class Party(val id : String)
  def transfer(p1: Party) {}
  def transfer(s: String) {}
}
package accounting {

class datastore {
  val p1: Party = null
  /* line: 3 */transfer(p1)
}
}