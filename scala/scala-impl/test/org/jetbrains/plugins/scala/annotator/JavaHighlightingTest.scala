package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.javaHighlighting.JavaHighlightingTestBase

class JavaHighlightingTest extends JavaHighlightingTestBase() {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11

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

    addDummyJavaFile(java)
    assertNothing(errorsFromScalaCode(scala))
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

    addDummyJavaFile(java)
    assertNothing(errorsFromScalaCode(scala))
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

    addDummyJavaFile(java)
    assertNothing(errorsFromScalaCode(scala))
  }

  //SCL-10385 (also SCL-21216, SCL-21217)
  def testSCL10385(): Unit = {
    addDummyJavaFile(
      """public @interface Singleton {
        |}
        |""".stripMargin)
    addDummyJavaFile(
      """
        |public @interface Inject {
        |    boolean optional() default false;
        |}""".stripMargin
    )

    //FIXME: we expect no errors in this code, but currently it doesn't work
    assertErrorsText(
      """case class IdeBugFail @Inject() @Singleton()(var1: String)""".stripMargin,
      """Error(IdeBugFail,case classes without a parameter list are not allowed)
        |Error(Singleton()(var1: String),Annotation type expected)
        |Error(var1,Cannot resolve symbol var1)""".stripMargin,
    )
  }

  //SCL-11283
  def testSCL11283(): Unit = {
    assertNothing(errorsFromJavaCode(
      """import scala.collection.JavaConverters$;
        |import scala.collection.immutable.Map;
        |
        |public class Whatever {
        |    public <K, V> Map<K, V> convert(java.util.Map<K, V> m) {
        |        return JavaConverters$.MODULE$.mapAsScalaMapConverter(m).asScala().toMap(
        |                scala.Predef$.MODULE$.<scala.Tuple2<K, V>>conforms()
        |        );
        |    }
        |}""".stripMargin,
      "Whatever"
    ))
  }
}
