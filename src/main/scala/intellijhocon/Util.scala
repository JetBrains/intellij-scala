package intellijhocon

object Util {

  implicit class CharSequenceOps(cs: CharSequence) {
    def startsWith(str: String) =
      cs.length >= str.length && str.contentEquals(cs.subSequence(0, str.length))

    def contains(char: Char) =
      Iterator.range(0, cs.length).map(cs.charAt).contains(char)
  }

}
