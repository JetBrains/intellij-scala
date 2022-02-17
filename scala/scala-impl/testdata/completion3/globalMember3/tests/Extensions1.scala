package tests

object Extensions1:
  extension (str: String)
    def firstThreeChars: String = str.take(3)
