package org.jetbrains.plugins.scala.projectHighlighting.downloaded

import com.intellij.openapi.util.TextRange
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.projectHighlighting.base.GithubRepositoryWithRevision

class MeerkatProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {

  override protected def githubRepositoryWithRevision: GithubRepositoryWithRevision =
    GithubRepositoryWithRevision("niktrop", "Meerkat", "5013864a9cbcdb43f92d1d57200352743d412235")

  override def projectJdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_1_8

  import org.jetbrains.plugins.scala.util.TextRangeUtils.ImplicitConversions.tupleToTextRange

  override protected def filesWithProblems: Map[String, Set[TextRange]] = Map(
    "src/main/scala/org/meerkat/parsers/OperatorParsers.scala" -> Set(
      (2148, 2153), // Overriding type ((Int, Int), (Int, Int)) => this.o.SequenceBuilder does not conform to base type (T1, T2) => R
      (2182, 2197), // Expression of type this.o.SequenceBuilder doesn't conform to expected type SequenceBuilder[this.o.V]
      (15819, 15826), // Type mismatch, expected: this.OperatorSequence, actual: OperatorSequence[V]
      (15843, 15844), // Expression of type o.OperatorSequenceBuilder doesn't conform to expected type OperatorSequenceBuilder[V]
      (16037, 16044), // Type mismatch, expected: this.OperatorSequence, actual: OperatorSequence[V]
      (16062, 16063), // Expression of type o.OperatorSequenceBuilder doesn't conform to expected type OperatorSequenceBuilder[V]
      (16260, 16267), // Type mismatch, expected: this.OperatorSequence, actual: OperatorSequence[V]
      (16289, 16290), // Expression of type o.OperatorSequenceBuilder doesn't conform to expected type OperatorSequenceBuilder[V]
      (19250, 19255), // Overriding type ((Int, Int)) => AbstractNonterminal[EBNF[V]#OptOrSeq] does not conform to base type T1 => R
      (19270, 19279), // Expression of type AbstractNonterminal[EBNF[V]#OptOrSeq] doesn't conform to expected type AbstractNonterminal[ebnf.OptOrSeq]
    ),
    "src/main/scala/org/meerkat/parsers/Parsers.scala" -> Set(
      (15201, 15209), // Type mismatch, expected: this.V => NotInferredU, actual: ebnf.OptOrSeq ~ V => ebnf.OptOrSeq
      (15638, 15646), // Type mismatch, expected: this.V => NotInferredU, actual: ebnf.OptOrSeq ~ V => ebnf.OptOrSeq
      (16100, 16128), // Type mismatch, expected: AbstractAlternationBuilder[NonPackedNode, ebnf.OptOrSeq], actual: AlternationBuilder[Any]
      (16543, 16551), // Type mismatch, expected: this.V => NotInferredU, actual: ebnf.OptOrSeq ~ V => ebnf.OptOrSeq
      (16983, 16991), // Type mismatch, expected: this.V => NotInferredU, actual: ebnf.OptOrSeq ~ V => ebnf.OptOrSeq
      (17505, 17513), // Type mismatch, expected: this.V => NotInferredU, actual: ebnf.OptOrSeq ~ V => ebnf.OptOrSeq
    ),
    "src/main/scala/org/meerkat/parsers/package.scala" -> Set(
      (4162, 4227), // Expression of type Parsers.AbstractNonterminal[this.V] doesn't conform to expected type Parsers.AbstractNonterminal[T]
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example1.scala" -> Set(
      (1739, 1740), // Cannot resolve symbol ~
      (1761, 1764), // Cannot resolve overloaded method 'syn'
      (1769, 1770), // Cannot resolve symbol ~
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example10.scala" -> Set(
      (1737, 1787), // Expression of type Any doesn't conform to expected type Nonterminal & BinaryOp
      (1870, 1873), // Cannot resolve overloaded method 'syn'
      (1923, 1933), // Cannot resolve symbol reduceLeft
      (1935, 1936), // Missing parameter type
      (1945, 1950), // 'op.type' does not take parameters
      (2011, 2012), // Cannot resolve symbol *
      (2065, 2066), // Cannot resolve symbol -
      (2128, 2129), // Type mismatch, expected: String, actual: Any
      (2142, 2143), // Cannot resolve overloaded method '|'
      (2172, 2173), // Cannot resolve symbol ^
      (2314, 2339), // Expression of type OperatorSequenceBuilder[this.o.V] doesn't conform to expected type OperatorSequenceBuilder[BinaryOp ~ List[Int]]
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example11.scala" -> Set(
      (1721, 1724), // Cannot resolve overloaded method 'syn'
      (1795, 1796), // Cannot resolve overloaded method '|'
      (1881, 1886), // Cannot resolve overloaded method 'parse'
      (1923, 1932), // Cannot resolve symbol isSuccess
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example12.scala" -> Set(
      (1782, 1785), // Cannot resolve overloaded method 'syn'
      (1831, 1832), // Type mismatch, expected: Exp, actual: Any
      (1880, 1881), // Type mismatch, expected: Exp, actual: this.o.V
      (1938, 1939), // Type mismatch, expected: Exp, actual: Any
      (2000, 2001), // Type mismatch, expected: Exp, actual: Any
      (2063, 2064), // Type mismatch, expected: Exp, actual: Any
      (2125, 2126), // Type mismatch, expected: Exp, actual: Any
      (2166, 2167), // Cannot resolve overloaded method '|'
      (2191, 2192), // Cannot resolve symbol ^
      (2195, 2196), // Missing parameter type
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example13.scala" -> Set(
      (1771, 1774), // Cannot resolve overloaded method 'syn'
      (1850, 1851), // Cannot resolve overloaded method '|'
      (1856, 1859), // Cannot resolve symbol map
      (1862, 1863), // Missing parameter type
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example14.scala" -> Set(
      (1782, 1785), // Cannot resolve overloaded method 'syn'
      (1793, 1794), // Cannot resolve overloaded method '~'
      (1799, 1802), // Cannot resolve symbol map
      (1807, 1812), // Cannot resolve symbol toInt
      (1835, 1840), // Type mismatch, expected: Nonterminal, actual: Any
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example15.scala" -> Set(
      (1703, 1706), // Cannot resolve overloaded method 'syn'
      (1741, 1742), // Type mismatch, expected: String, actual: Any
      (1789, 1790), // Cannot resolve symbol *
      (1807, 1808), // Cannot resolve overloaded method '|'
      (1821, 1822), // Cannot resolve symbol ^
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example2.scala" -> Set(
      (1761, 1764), // Cannot resolve overloaded method 'syn'
      (1769, 1770), // Cannot resolve symbol ~
      (1886, 1889), // Cannot resolve overloaded method 'syn'
      (1894, 1895), // Cannot resolve symbol *
      (1908, 1910), // Cannot resolve symbol :+
      (1996, 1999), // Cannot resolve overloaded method 'syn'
      (2004, 2005), // Cannot resolve symbol *
      (2018, 2020), // Cannot resolve symbol :+
      (2074, 2077), // Cannot resolve overloaded method 'syn'
      (2082, 2083), // Cannot resolve symbol +
      (2096, 2098), // Cannot resolve symbol :+
      (2152, 2155), // Cannot resolve overloaded method 'syn'
      (2160, 2161), // Cannot resolve symbol ?
      (2174, 2176), // Cannot resolve symbol :+
      (2230, 2233), // Cannot resolve overloaded method 'syn'
      (2239, 2240), // Cannot resolve symbol ~
      (2264, 2270), // Cannot resolve symbol concat
      (2346, 2349), // Cannot resolve overloaded method 'syn'
      (2354, 2355), // Cannot resolve symbol ~
      (2397, 2400), // Cannot resolve overloaded method 'syn'
      (2405, 2407), // Cannot resolve symbol **
      (2438, 2441), // Cannot resolve overloaded method 'syn'
      (2446, 2447), // Cannot resolve symbol +
      (2479, 2482), // Cannot resolve overloaded method 'syn'
      (2487, 2488), // Cannot resolve symbol ?
      (2519, 2522), // Cannot resolve overloaded method 'syn'
      (2528, 2529), // Cannot resolve symbol ~
      (2556, 2559), // Cannot resolve overloaded method 'syn'
      (2564, 2565), // Cannot resolve symbol \
      (2588, 2591), // Cannot resolve overloaded method 'syn'
      (2596, 2597), // Cannot resolve symbol *
      (2622, 2625), // Cannot resolve overloaded method 'syn'
      (2630, 2631), // Cannot resolve symbol ~
      (2634, 2635), // Cannot resolve symbol ?
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example3.scala" -> Set(
      (1705, 1708), // Cannot resolve overloaded method 'syn'
      (1716, 1717), // Cannot resolve overloaded method '~'
      (1741, 1747), // Cannot resolve symbol concat
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example4.scala" -> Set(
      (1711, 1714), // Cannot resolve overloaded method 'syn'
      (1749, 1750), // Type mismatch, expected: String, actual: Any
      (1797, 1798), // Cannot resolve symbol *
      (1815, 1816), // Cannot resolve overloaded method '|'
      (1829, 1830), // Cannot resolve symbol ^
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example5.scala" -> Set(
      (1654, 1915), // Expression of type Any doesn't conform to expected type Nonterminal
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example6.scala" -> Set(
      (1687, 1712), // Expression of type Any doesn't conform to expected type OperatorNonterminal
      (1756, 1759), // Cannot resolve overloaded method 'syn'
      (1792, 1798), // Cannot resolve symbol concat
      (1804, 1805), // Cannot resolve overloaded method '|'
      (1810, 1811), // Cannot resolve symbol ^
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example7.scala" -> Set(
      (1752, 1755), // Cannot resolve overloaded method 'syn'
      (1826, 1827), // Cannot resolve symbol *
      (1906, 1907), // Type mismatch, expected: String, actual: Any
      (1953, 1954), // Cannot resolve symbol -
      (1968, 1969), // Cannot resolve overloaded method '|'
      (1993, 1994), // Cannot resolve symbol ^
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example8.scala" -> Set(
      (1799, 1991), // Expression of type Any doesn't conform to expected type OperatorNonterminal
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example9.scala" -> Set(
      (1850, 2053), // Expression of type Any doesn't conform to expected type OperatorNonterminal
    )
  )
}
