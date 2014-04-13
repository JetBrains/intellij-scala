package intellijhocon

import com.intellij.lang.PsiBuilder
import intellijhocon.HoconTokenType.UnquotedChars

/**
 * Parser combinator DSL inspired by Scala parser combinators library.
 */
trait GenParser {
  self =>

  def parse(psiBuilder: PsiBuilder): Boolean

  def ~(other: => GenParser): GenParser = new GenParser {
    def parse(psiBuilder: PsiBuilder) = {
      val mark = psiBuilder.mark()
      if (self.parse(psiBuilder) && other.parse(psiBuilder)) {
        mark.drop()
        true
      } else {
        mark.rollbackTo()
        false
      }
    }
  }

  def |(other: => GenParser): GenParser = new GenParser {
    def parse(psiBuilder: PsiBuilder) = {
      val mark = psiBuilder.mark
      if (self.parse(psiBuilder)) {
        mark.drop()
        true
      } else {
        mark.rollbackTo()
        other.parse(psiBuilder)
      }
    }
  }

  def ? : GenParser = new GenParser {
    def parse(psiBuilder: PsiBuilder) = {
      self.parse(psiBuilder)
      true
    }
  }

  def * : GenParser = new GenParser {
    def parse(psiBuilder: PsiBuilder) = {
      while (self.parse(psiBuilder)) ()
      true
    }
  }

  def + : GenParser = this ~ this.*

  def marking(func: PsiBuilder.Marker => Any) = new GenParser {
    def parse(psiBuilder: PsiBuilder) = {
      val mark = psiBuilder.mark()
      if (self.parse(psiBuilder)) {
        func(mark)
        true
      } else {
        mark.drop()
        false
      }
    }
  }

  def as(element: HoconElementType): GenParser =
    marking(_.done(element))

  def asError(message: String): GenParser =
    marking(_.error(message))

}

object GenParser {
  implicit def tokenParser(token: HoconTokenType): GenParser =
    matchWhen(_.getTokenType == token)

  implicit def unquotedParser(text: String): GenParser =
    matchWhen { psiBuilder =>
      psiBuilder.getTokenType == UnquotedChars && psiBuilder.getTokenText == text
    }

  def not(tokens: HoconTokenType*) =
    matchWhen(b => b.getTokenType != null && !tokens.contains(b.getTokenType))

  def matchWhen(p: PsiBuilder => Boolean) = new GenParser {
    def parse(psiBuilder: PsiBuilder) =
      if (p(psiBuilder)) {
        psiBuilder.advanceLexer()
        true
      } else false
  }

}
