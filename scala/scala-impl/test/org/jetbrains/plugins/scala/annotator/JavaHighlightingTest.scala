package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.javaHighlighting.JavaHighlightingTestBase

/**
  * @author Anton Yalyshev
  * @since 06/09/18
  */
class JavaHighlightingTest extends JavaHighlightingTestBase() {

  def testSCL12136(): Unit = {
    val scala =
      """
        |import OfferRepository._
        |
        |class OfferService {
        |  val repository = new OfferRepository
        |
        |  def getOffers(rule: Rule[_]) = repository.findOffersByRule(rule)
        |}
      """.stripMargin

    val java =
      """
        |import java.io.Serializable;
        |import java.util.HashSet;
        |import java.util.List;
        |import java.util.Set;
        |import java.util.stream.Collectors;
        |
        |public class OfferRepository
        |{
        |  public List<Offer> findOffersByRule(final Rule<?> rule)
        |  {
        |    return Offer.getAllOffers()
        |                .stream()
        |                .filter(offer -> offer.getRules().contains(rule))
        |                .collect(Collectors.toList());
        |  }
        |
        |  public static class Offer
        |  {
        |    private Set<Rule> rules = new HashSet<Rule>();
        |
        |    public Set<Rule> getRules()
        |    {
        |      return rules;
        |    }
        |
        |    public static Set<Offer> getAllOffers()
        |    {
        |      return new HashSet<Offer>();
        |    }
        |  }
        |
        |  public abstract static class Rule<T extends Serializable>
        |  {
        |  }
        |}
      """.stripMargin

    assertNothing(errorsFromScalaCode(scala, java))
  }

  def testSCL18045(): Unit = {
    val java =
      """
        |import java.util.List;
        |
        |public class Foo<T> implements List<T> {
        | public static class Bar<A, B> {}
        |
        | public Foo<Bar<T, ?>> foo() { return null; };
        |}
        |""".stripMargin

    val scala =
      """
        |object U {
        |  val foo = new Foo[String]
        |  val bar = new Foo.Bar[String, Int]
        |  foo.foo().add(bar)
        |}
        |""".stripMargin

    assertNothing(errorsFromScalaCode(scala, java))
  }

  def testSCL10930(): Unit = {
    val scala =
      """
        |  def testThis2(): Range[Integer] = {
        |    Range.between(1, 3)
        |  }
      """.stripMargin

    val java =
      """
        |import java.util.Comparator;
        |
        |public class Range<T> {
        |
        |    private Range(T element1, T element2, Comparator<T> comparator) {
        |    }
        |
        |    public static <T extends Comparable<T>> Range<T> between(T fromInclusive, T toInclusive) {
        |        return between(fromInclusive, toInclusive, null);
        |    }
        |
        |    public static <T> Range<T> between(T fromInclusive, T toInclusive, Comparator<T> comparator) {
        |        return new Range<T>(fromInclusive, toInclusive, comparator);
        |    }
        |}
      """.stripMargin

    assertNothing(errorsFromScalaCode(scala, java))
  }

}
