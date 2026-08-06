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
extending [`com.intellij.lang.ParserDefinition`](https://github.com/JetBrains/intellij-community/blob/7503919/platform/core-api/src/com/intellij/lang/ParserDefinition.java). Scala plugin has a base class [`ScalaParserDefinitionBase`](https://github.com/jetbrains/intellij-scala/blob/b2eed7fb7c5aebc2ac9aa709b4de2483b1112036/scala/scala-impl/src/org/jetbrains/plugins/scala/lang/parser/ScalaParserDefinitionBase.scala)
extending [`com.intellij.lang.ParserDefinition`](https://github.com/JetBrains/intellij-community/blob/7503919/platform/core-api/src/com/intellij/lang/ParserDefinition.java), which is in turn extended by

- [`ScalaParserDefinition`](https://github.com/jetbrains/intellij-scala/blob/b2eed7fb7c5aebc2ac9aa709b4de2483b1112036/scala/scala-impl/src/org/jetbrains/plugins/scala/lang/parser/ScalaParserDefinition.scala)
- [`Scala3ParserDefinition`](https://github.com/jetbrains/intellij-scala/blob/b2eed7fb7c5aebc2ac9aa709b4de2483b1112036/scala/scala-impl/src/org/jetbrains/plugins/scala/lang/parser/Scala3ParserDefinition.scala)
- [`SbtParserDefinition`](https://github.com/jetbrains/intellij-scala/blob/ab31d09e1d7666a65e3638659917c80264e6b910/sbt/sbt-impl/src/org/jetbrains/sbt/language/SbtParserDefinition.scala)
- [`WorksheetParserDefinition`](https://github.com/jetbrains/intellij-scala/blob/909be8a4c83ece6b2fb977abfe787bfd4426d3be/scala/worksheet/src/org/jetbrains/plugins/scala/worksheet/WorksheetParserDefinition.scala)
- [`WorksheetParserDefinition3`](https://github.com/jetbrains/intellij-scala/blob/909be8a4c83ece6b2fb977abfe787bfd4426d3be/scala/worksheet/src/org/jetbrains/plugins/scala/worksheet/WorksheetParserDefinition3.scala) - worksheets for Scala 3

## Lexer

There is a big layered hierarchy of lexers.

At the bottom of the hierarchy, there are lexers generated from [JFlex](https://jflex.de/) `.flex` files, such as:

- [`org.jetbrains.plugins.scala.lang.lexer.core._ScalaCoreLexer`](./lexer/core/_ScalaCoreLexer.java)
- [`org.jetbrains.plugins.scala.lang.lexer.core._ScalaSplittingLexer`](./lexer/core/_ScalaSplittingLexer.java)
- [`org.jetbrains.plugins.scala.lang.scaladoc.lexer._ScalaDocLexer`](./scaladoc/lexer/_ScalaDocLexer.java)
- [`org.jetbrains.plugins.scalaDirective.lang.lexer._ScalaDirectiveLexer`](../../scalaDirective/lang/lexer/_ScalaDirectiveLexer.java)

They are generated using the Ant buildfile [`tools/lexer/build.xml`](../../../../../../../../tools/lexer/build.xml).
Please use JDK11 when running any of these targets. This can be configured in the Ant tool window's "Build File Properties" panel, in the "Execution" tab.

Examples (ignoring technologies not related to plain Scala, like Play, SSP, SBT):

- [`org.jetbrains.plugins.scala.lang.lexer.ScalaLexer`](./lexer/ScalaLexer.java)
- [`org.jetbrains.plugins.scala.lang.lexer.ScalaPlainLexer`](./lexer/ScalaPlainLexer.scala)
- [`org.jetbrains.plugins.scala.lang.lexer.ScalaPlainLexer.ScalaFlexLexer`](./lexer/ScalaPlainLexer.scala)
- [`org.jetbrains.plugins.scala.lang.lexer.ScalaPlainLexer.ScalaSplittingFlexLexer`](./lexer/ScalaPlainLexer.scala)
- [`org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighter.CustomScalaLexer`](../highlighter/ScalaSyntaxHighlighter.scala)
- [`org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighter.ScalaHtmlHighlightingLexerWrapper`](../highlighter/ScalaSyntaxHighlighter.scala)
- [`org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocAsteriskStripperLexer`](./scaladoc/lexer/ScalaDocLexer.scala)
- [`org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocLexer`](./scaladoc/lexer/ScalaDocLexer.scala)
- [`org.jetbrains.plugins.scala.lang.lexer.ScalaXmlLexer`](./lexer/ScalaXmlLexer.scala)

## Parser

The entry point for the Scala parser implementation is
at [`scala/scala-impl/src/org/jetbrains/plugins/scala/lang/parser/ScalaParser.scala`](./parser/ScalaParser.scala).

Most of actual parsing happens in `scala/scala-impl/src/org/jetbrains/plugins/scala/lang/parser/parsing`.

### Configuring parser’s behavior

Note that [`org.jetbrains.plugins.scala.lang.parser.ScalaParser`](./parser/ScalaParser.scala) takes `isScala3` as an argument,
which means that we have a single parser that can parse both Scala 2 and Scala 3 syntax. Also, note that there
is [`org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder`](./parser/parsing/builder/ScalaPsiBuilder.scala), which is used to configure the parser’s behavior. Actually, `isScala3` argument passed to [`ScalaParser`](./parser/ScalaParser.scala) is passed down to [`ScalaPsiBuilder`](./parser/parsing/builder/ScalaPsiBuilder.scala).

### Parsing an expression

Most parsing happens by objects that implement [`org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule`](./parser/parsing/ParsingRule.scala) and
override `def parse(implicit builder: ScalaPsiBuilder): Boolean`, which returns `true` if parsing was successful. See,
for example, function parameter
parsing: [`org.jetbrains.plugins.scala.lang.parser.parsing.params.Param`](./parser/parsing/params/Param.scala).

### Scala tokens and elements

There are [`org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType`](./lexer/ScalaTokenType.scala) and [`org.jetbrains.plugins.scala.lang.parser.ScalaElementType`](./parser/ScalaElementType.scala), which extend [`com.intellij.psi.tree.IElementType`](https://github.com/jetbrains/intellij-community/blob/7503919/platform/core-api/src/com/intellij/psi/tree/IElementType.java).

### Language substitutor

One should also be aware of [`org.jetbrains.plugins.scala.lang.parser.ScalaLanguageSubstitutor`](parser/ScalaLanguageSubstitutor.scala).