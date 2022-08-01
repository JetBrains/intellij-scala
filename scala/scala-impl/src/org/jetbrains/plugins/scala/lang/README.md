# Scala Lexer/Parser notes

To familiarize yourself with the IntelliJ Platform SDK,
see the [docs](https://plugins.jetbrains.com/docs/intellij/welcome.html).

There’s a whole separate [section](https://plugins.jetbrains.com/docs/intellij/custom-language-support.html) ("Part VII
Custom Languages") devoted to creating a plugin for a custom language. The section also includes
a [tutorial](https://plugins.jetbrains.com/docs/intellij/custom-language-support-tutorial.html), which provides a
hands-on approach; keep in mind, however, that the tutorial
uses [Grammar-Kit](https://github.com/JetBrains/Grammar-Kit)
to generate a parser, while the Scala parser is a
handwritten [recursive descent parser](https://en.wikipedia.org/wiki/Recursive_descent_parser).

Links into the docs:

- explanation
    - Lexer: <https://plugins.jetbrains.com/docs/intellij/implementing-lexer.html>
    - Parser: <https://plugins.jetbrains.com/docs/intellij/implementing-parser-and-psi.html>
- tutorial
    - Grammar and Parser: <https://plugins.jetbrains.com/docs/intellij/grammar-and-parser.html>
    - Lexer and Parser Definition: <https://plugins.jetbrains.com/docs/intellij/lexer-and-parser-definition.html>

## Parser Definition

A plugin needs to let IntelliJ Platform know how to create a lexer and parser objects. This is done by
extending `com.intellij.lang.ParserDefinition`. Scala plugin has a base class `ScalaParserDefinitionBase`
extending `com.intellij.lang.ParserDefinition`, which is in turn extended by

- `ScalaParserDefinition`
- `Scala3ParserDefinition`
- `SbtParserDefinition`
- `WorksheetParserDefinition`
- `WorksheetParserDefinition3` - worksheets for Scala 3

## Lexer

There is a big layered hierarchy of lexers.

At the bottom of the hierarchy, there are lexers generated from `.flex` files:

- `org.jetbrains.plugins.scala.lang.lexer.core._ScalaCoreLexer`
- `org.jetbrains.plugins.scala.lang.lexer.core._ScalaSplittingLexer`
- `org.jetbrains.plugins.scala.lang.scaladoc.lexer._ScalaDocLexer`

They are generated using build scripts located in `community/tools/build.xml` (there are other `.flex`-based lexers, but
they are not for the plain Scala) (e.g. see `tools/lexer/build.xml`)

Examples (ignoring technologies not related to plain Scala, like Play, SSP, SBT):

- `org.jetbrains.plugins.scala.lang.lexer.ScalaLexer`
- `org.jetbrains.plugins.scala.lang.lexer.ScalaPlainLexer`
- `org.jetbrains.plugins.scala.lang.lexer.ScalaPlainLexer.ScalaFlexLexer`
- `org.jetbrains.plugins.scala.lang.lexer.ScalaPlainLexer.ScalaSplittingFlexLexer`
- `org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighter.CustomScalaLexer`
- `org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighter.ScalaHtmlHighlightingLexerWrapper`
- `org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocAsteriskStripperLexer`
- `org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocLexer`
- `org.jetbrains.plugins.scala.lang.lexer.ScalaXmlLexer`

## Parser

The entry point for the Scala parser implementation is
at `scala/scala-impl/src/org/jetbrains/plugins/scala/lang/parser/ScalaParser.scala`.

Most of actual parsing happens at `scala/scala-impl/src/org/jetbrains/plugins/scala/lang/parser/parsing`.

### Configuring parser’s behavior

Note that `org.jetbrains.plugins.scala.lang.parser.ScalaParser` takes `isScala3` as an argument, which means that we
have a single parser that can parse both Scala 2 and Scala 3 syntax. Also, note that there
is `org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder`, which is used to configure the parser’s
behavior. Actually, `isScala3` argument passed to `ScalaParser` is passed down to `ScalaPsiBuilder`.

### Parsing an expression

Most parsing happens by objects that implement `org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule` and
override `def parse(implicit builder: ScalaPsiBuilder): Boolean`, which returns `true` is parsing was successful. See,
for example, a function parameter
parsing: `org.jetbrains.plugins.scala.lang.parser.parsing.params.Param` ([link](https://jetbrains.team/p/scl/repositories/intellij-scala/files/scala/scala-impl/src/org/jetbrains/plugins/scala/lang/parser/parsing/params/Param.scala))

### Scala tokens and elements

There are `org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType` and `org.jetbrains.plugins.scala.lang.parser.ScalaElementType`, which extend `com.intellij.psi.tree.IElementType`.

### Language substitutor

One should also be aware of `org.jetbrains.plugins.scala.lang.parser.ScalaLanguageSubstitutor`.