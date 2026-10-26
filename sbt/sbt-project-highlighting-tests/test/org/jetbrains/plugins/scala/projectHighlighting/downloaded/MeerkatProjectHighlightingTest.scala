package org.jetbrains.plugins.scala.projectHighlighting.downloaded

import com.intellij.openapi.util.TextRange
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.projectHighlighting.base.GithubRepositoryWithRevision

class MeerkatProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {

  override protected def githubRepositoryWithRevision: GithubRepositoryWithRevision =
    GithubRepositoryWithRevision("vasilmkd", "meerkat-test-fork", "0e531c905718d56a40d747bf8a863dee637da21b")

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
      (1754, 1755), // Cannot resolve symbol ~
      (1776, 1779), // Cannot resolve overloaded method 'syn'
      (1784, 1785), // Cannot resolve symbol ~
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example10.scala" -> Set(
      (1752, 1802), // Expression of type Any doesn't conform to expected type Nonterminal & BinaryOp
      (1885, 1888), // Cannot resolve overloaded method 'syn'
      (1938, 1948), // Cannot resolve symbol reduceLeft
      (1950, 1951), // Missing parameter type
      (1960, 1965), // 'op.type' does not take parameters
      (2026, 2027), // Cannot resolve symbol *
      (2080, 2081), // Cannot resolve symbol -
      (2143, 2144), // Type mismatch, expected: String, actual: Any
      (2157, 2158), // Cannot resolve overloaded method '|'
      (2187, 2188), // Cannot resolve symbol ^
      (2329, 2354), // Expression of type OperatorSequenceBuilder[this.o.V] doesn't conform to expected type OperatorSequenceBuilder[BinaryOp ~ List[Int]]
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example11.scala" -> Set(
      (1736, 1739), // Cannot resolve overloaded method 'syn'
      (1810, 1811), // Cannot resolve overloaded method '|'
      (1896, 1901), // Cannot resolve overloaded method 'parse'
      (1938, 1947), // Cannot resolve symbol isSuccess
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example12.scala" -> Set(
      (1797, 1800), // Cannot resolve overloaded method 'syn'
      (1846, 1847), // Type mismatch, expected: Exp, actual: Any
      (1895, 1896), // Type mismatch, expected: Exp, actual: this.o.V
      (1953, 1954), // Type mismatch, expected: Exp, actual: Any
      (2015, 2016), // Type mismatch, expected: Exp, actual: Any
      (2078, 2079), // Type mismatch, expected: Exp, actual: Any
      (2140, 2141), // Type mismatch, expected: Exp, actual: Any
      (2181, 2182), // Cannot resolve overloaded method '|'
      (2206, 2207), // Cannot resolve symbol ^
      (2210, 2211), // Missing parameter type
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example13.scala" -> Set(
      (1786, 1789), // Cannot resolve overloaded method 'syn'
      (1865, 1866), // Cannot resolve overloaded method '|'
      (1871, 1874), // Cannot resolve symbol map
      (1877, 1878), // Missing parameter type
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example14.scala" -> Set(
      (1797, 1800), // Cannot resolve overloaded method 'syn'
      (1808, 1809), // Cannot resolve overloaded method '~'
      (1814, 1817), // Cannot resolve symbol map
      (1822, 1827), // Cannot resolve symbol toInt
      (1850, 1855), // Type mismatch, expected: Nonterminal, actual: Any
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example15.scala" -> Set(
      (1703, 1706), // Cannot resolve overloaded method 'syn'
      (1741, 1742), // Type mismatch, expected: String, actual: Any
      (1789, 1790), // Cannot resolve symbol *
      (1807, 1808), // Cannot resolve overloaded method '|'
      (1821, 1822), // Cannot resolve symbol ^
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example2.scala" -> Set(
      (1776, 1779), // Cannot resolve overloaded method 'syn'
      (1784, 1785), // Cannot resolve symbol ~
      (1901, 1904), // Cannot resolve overloaded method 'syn'
      (1909, 1910), // Cannot resolve symbol *
      (1923, 1925), // Cannot resolve symbol :+
      (2011, 2014), // Cannot resolve overloaded method 'syn'
      (2019, 2020), // Cannot resolve symbol *
      (2033, 2035), // Cannot resolve symbol :+
      (2089, 2092), // Cannot resolve overloaded method 'syn'
      (2097, 2098), // Cannot resolve symbol +
      (2111, 2113), // Cannot resolve symbol :+
      (2167, 2170), // Cannot resolve overloaded method 'syn'
      (2175, 2176), // Cannot resolve symbol ?
      (2189, 2191), // Cannot resolve symbol :+
      (2245, 2248), // Cannot resolve overloaded method 'syn'
      (2254, 2255), // Cannot resolve symbol ~
      (2279, 2285), // Cannot resolve symbol concat
      (2361, 2364), // Cannot resolve overloaded method 'syn'
      (2369, 2370), // Cannot resolve symbol ~
      (2412, 2415), // Cannot resolve overloaded method 'syn'
      (2420, 2422), // Cannot resolve symbol **
      (2453, 2456), // Cannot resolve overloaded method 'syn'
      (2461, 2462), // Cannot resolve symbol +
      (2494, 2497), // Cannot resolve overloaded method 'syn'
      (2502, 2503), // Cannot resolve symbol ?
      (2534, 2537), // Cannot resolve overloaded method 'syn'
      (2543, 2544), // Cannot resolve symbol ~
      (2571, 2574), // Cannot resolve overloaded method 'syn'
      (2579, 2580), // Cannot resolve symbol \
      (2603, 2606), // Cannot resolve overloaded method 'syn'
      (2611, 2612), // Cannot resolve symbol *
      (2637, 2640), // Cannot resolve overloaded method 'syn'
      (2645, 2646), // Cannot resolve symbol ~
      (2649, 2650), // Cannot resolve symbol ?
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example3.scala" -> Set(
      (1720, 1723), // Cannot resolve overloaded method 'syn'
      (1731, 1732), // Cannot resolve overloaded method '~'
      (1756, 1762), // Cannot resolve symbol concat
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example4.scala" -> Set(
      (1726, 1729), // Cannot resolve overloaded method 'syn'
      (1764, 1765), // Type mismatch, expected: String, actual: Any
      (1812, 1813), // Cannot resolve symbol *
      (1830, 1831), // Cannot resolve overloaded method '|'
      (1844, 1845), // Cannot resolve symbol ^
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example5.scala" -> Set(
      (1669, 1930), // Expression of type Any doesn't conform to expected type Nonterminal
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example6.scala" -> Set(
      (1702, 1727), // Expression of type Any doesn't conform to expected type OperatorNonterminal
      (1771, 1774), // Cannot resolve overloaded method 'syn'
      (1807, 1813), // Cannot resolve symbol concat
      (1819, 1820), // Cannot resolve overloaded method '|'
      (1825, 1826), // Cannot resolve symbol ^
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example7.scala" -> Set(
      (1767, 1770), // Cannot resolve overloaded method 'syn'
      (1841, 1842), // Cannot resolve symbol *
      (1921, 1922), // Type mismatch, expected: String, actual: Any
      (1968, 1969), // Cannot resolve symbol -
      (1983, 1984), // Cannot resolve overloaded method '|'
      (2008, 2009), // Cannot resolve symbol ^
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example8.scala" -> Set(
      (1814, 2006), // Expression of type Any doesn't conform to expected type OperatorNonterminal
    ),
    "src/test/scala/org/meerkat/parsers/examples/Example9.scala" -> Set(
      (1865, 2068), // Expression of type Any doesn't conform to expected type OperatorNonterminal
    )
  )
}
