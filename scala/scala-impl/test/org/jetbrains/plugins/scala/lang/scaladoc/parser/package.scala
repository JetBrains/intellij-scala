package org.jetbrains.plugins.scala.lang.scaladoc

package object parser {
  def checkWhitespaceTokensOnlyContainWhitespacs(tree: String): Unit = {
    val wsRegex = raw"\s+ScPsiDocToken\(DOC_WHITESPACE\)\('(.*)'\)".r
    val allowedWs = raw"(\s|\\n)*".r
    for {
      line <- tree.linesIterator
      m <- wsRegex.findAllMatchIn(line)
      wsToken = m.group(1)
    } {
      assert(allowedWs.matches(wsToken), s"Whitespace token contained non-ws character: '$wsToken'")
    }
  }
}
