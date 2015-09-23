package org.jetbrains.plugins.scala
package javaHighlighting

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, ScalaAnnotator, _}
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.junit.Assert


/**
 * Author: Svyatoslav Ilinskiy
 * Date: 7/8/15
 */
class JavaHighlightingTest extends ScalaFixtureTestCase {

  def testProtected() = {
    val scala =
      """
        |class MeaningOfLifeSpec {
        |  val c = new UltimateQuestion {}
        |  def meaningOfLifeScala() {
        |    c.meaningOfLife()
        |  }
        |}
      """.stripMargin
    val java =
      """
        |public class UltimateQuestion {
        |    protected int meaningOfLife() {
        |        return 42; //Answer to the Ultimate Question of Life, the Universe, and Everything
        |    }
        |}
      """.stripMargin
    assertNoErrors(messagesFromScalaCode(scala, java))
  }

  def testValueTypes(): Unit = {
    val scala =
      """
        |class Order(val limitPrice: Price, val qty: Quantity)
        |class Prices(val prices: java.util.List[Price])
        |
        |class Price(val doubleVal: Double) extends AnyVal
        |class Quantity(val doubleVal: Double) extends AnyVal
        |class Bar
        |class BarWrapper(val s: Bar) extends AnyVal
        |class BarWrappers(val bars: java.util.List[BarWrapper])
        |
      """.stripMargin
    val java =
      """
        |import java.util.ArrayList;
        |
        |public class JavaHighlightingValueTypes {
        |
        |    public static void main(String[] args) {
        |        Order o = new Order(19.0, 10);
        |        System.out.println("Hello World! " + o.limitPrice());
        |        Price p = new Price(10);
        |
        |        Prices pr = new Prices(new ArrayList<Price>());
        |        BarWrappers barWrappers = new BarWrappers(new ArrayList<Bar>());
        |
        |        doublePrice(new Price(10.0));
        |        doublePrice(42.0);
        |    }
        |
        |    public static void doublePrice(Price p) {
        |        System.out.println(p.doubleVal() * 2);
        |    }
        |
        |}
      """.stripMargin

    assertMatches(messagesFromJavaCode(scala, java, javaClassName = "JavaHighlightingValueTypes")) {
      case Error("(42.0)", CannotBeApplied()) :: Nil =>
    }
  }

  def testOptionApply(): Unit = {
    val java =
      """
        |import scala.Option;
        |
        |public abstract class OptionApply {
        |
        |    public OptionApply() {
        |        setAction(Option.apply("importVCardFile"));
        |    }
        |
        |    public abstract void setAction(Option<String> bar);
        |}
      """.stripMargin


    assertNoErrors(messagesFromJavaCode(scalaFileText = "", java, javaClassName = "OptionApply"))
  }

  def testAccessBacktick(): Unit = {
    val scala =
      """
        |import scala.beans.BeanProperty
        |
        |case class TestAccessBacktick(@BeanProperty `type`:String)
      """.stripMargin

    val java =
      """
        |public class TestJavaAAA {
        |    public static void main(String[] args) {
        |        TestAccessBacktick t = new TestAccessBacktick("42");
        |        t.type();
        |        t.getType();
        |        t.get$u0060type$u0060();
        |    }
        |}
      """.stripMargin

    assertMatches(messagesFromJavaCode(scala, java, javaClassName = "TestJavaAAA")) {
      case Error("get$u0060type$u0060", CannotResolveMethod()) :: Nil =>
    }
  }

  def testMultipleThrowStatements(): Unit = {
    val scala = ""
    val java =
      """
        |import scala.concurrent.Await;
        |import scala.concurrent.Future;
        |import scala.concurrent.duration.Duration;
        |
        |import java.util.concurrent.TimeoutException;
        |
        |public class ThrowsJava {
        |    public void bar(Future<Integer> scalaFuture) {
        |        try {
        |            Await.ready(scalaFuture, Duration.Inf());
        |        } catch (InterruptedException e) {
        |            e.printStackTrace();
        |        } catch (TimeoutException e) {
        |            e.printStackTrace();
        |        }
        |    }
        |}
      """.stripMargin

    assertNoErrors(messagesFromJavaCode(scala, java, javaClassName = "ThrowsJava"))
  }

  def testOverrideFinal(): Unit = {
    val scala = ""
    val java =
      """
        |import scala.Function1;
        |import scala.concurrent.ExecutionContext;
        |
        |public abstract class Future<T> implements scala.concurrent.Future<T> {
        |
        |    @Override
        |    public scala.concurrent.Future<T> withFilter(Function1<T, Object> pred, ExecutionContext executor) {
        |        return null;
        |    }
        |}
      """.stripMargin

    assertNoErrors(messagesFromJavaCode(scala, java, "Future"))
  }

  def testSCL5617Option(): Unit = {
    val scala = ""
    val java =
      """
        |import scala.Function1;
        |import scala.Option;
        |import scala.runtime.BoxedUnit;
        |import java.util.concurrent.atomic.AtomicReference;
        |import scala.runtime.AbstractFunction1;
        |
        |public class SCL5617 {
        |     public static void main(String[] args) {
        |        AtomicReference<Function1<Object, BoxedUnit>> f = new AtomicReference<Function1<Object, BoxedUnit>>(new AbstractFunction1<Object, BoxedUnit>() {
        |          public BoxedUnit apply(Object o) {
        |            Option<String> option = Option.empty();
        |            return BoxedUnit.UNIT;
        |          }
        |        });
        |
        |        Option<Function1<Object, BoxedUnit>> o = Option.apply(f.get());
        |    }
        |}
        |
      """.stripMargin

    assertNoErrors(messagesFromJavaCode(scala, java, "SCL5617"))
  }

  def testCaseClassImplement() = {
    val scala = "case class CaseClass()"
    val java =
      """
        |public class CaseClassExtended extends CaseClass {
        |
        |}
      """.stripMargin

    assertNoErrors(messagesFromJavaCode(scala, java, javaClassName = "CaseClassExtended"))
  }


  def testClassParameter(): Unit = {
    val scala =
      """
        |class ScalaClass (var name: String, var surname: String)
        |
        |object Start {
        |  def main(args: Array[String]) {
        |    val scalaClassObj = new ScalaClass("Dom", "Sien")
        |    println(scalaClassObj.name)
        |    println(scalaClassObj.surname)
        |
        |    val javaClassObj = new JavaClass("Dom2", "Sien2", 31)
        |    println(javaClassObj.name)
        |    println(javaClassObj.surname)
        |    println(javaClassObj.getAge)
        |  }
        |}
      """.stripMargin

    val java =
      """
        |public class JavaClass extends ScalaClass {
        |  private int age;
        |
        |  public JavaClass(String name, String surname, int age) {
        |    super(name, surname);
        |    this.age = age;
        |  }
        |
        |  public int getAge() {
        |    return age;
        |  }
        |
        |  public void setAge(int age) {
        |    this.age = age;
        |  }
        |}
      """.stripMargin

    assertNoErrors(messagesFromJavaCode(scala, java, "JavaClass"))
    assertNoErrors(messagesFromScalaCode(scala, java))
  }

  def testSCL3390ParamAccessor(): Unit = {
    val scalaCode =
      """
        |object ScalaClient {
        |  def main(args: Array[String]) {
        |    new Sub(1).x
        |  }
        |}
        |
        |class Super(val x: Int)
        |
        |class Sub(x: Int) extends Super(x)
      """.stripMargin
    val javaCode =
      """
        |public class JavaClientSCL3390 {
        |    public static void main(String[] args) {
        |        new Sub(1).x();
        |    }
        |}
      """.stripMargin
    assertNoErrors(messagesFromJavaCode(scalaCode, javaCode, "JavaClientSCL3390"))
    assertNoErrors(messagesFromScalaCode(scalaCode, javaCode))
  }

  def testSCL3498ExistentialTypesFromJava(): Unit = {
    val javaCode =
      """
        |public @interface Transactional {
        |    Class<? extends Throwable>[] noRollbackFor() default {};
        |}
      """.stripMargin
    val scalaCode =
      """
        |@Transactional(noRollbackFor = Array(classOf[RuntimeException])) // expected Array[Class[_ <: Throwable] found Array[Class[RuntimeException]]
        |class A
      """.stripMargin

    assertNoErrors(messagesFromScalaCode(scalaCode, javaCode))
  }

  def testResolvePublicJavaFieldSameNameAsMethod(): Unit = {
    val scalaCode =
      """
        |package SCL3679
        |
        |object ResolvePublicJavaFieldSameNameAsMethod {
        |  def main(args: Array[String]) {
        |    println("foo")
        |    new ResolvePublicJavaFieldSameNameAsMethodJavaClass().hasIsCompressed
        |  }
        |}
      """.stripMargin

    val javaCode =
      """
        |package SCL3679;
        |
        |public class ResolvePublicJavaFieldSameNameAsMethodJavaClass {
        |    public boolean hasIsCompressed;
        |    public boolean hasIsCompressed() {
        |        System.out.println("In the method!");
        |        return hasIsCompressed;
        |    }
        |
        |}
      """.stripMargin

    assertNoErrors(messagesFromScalaCode(scalaCode, javaCode))
  }

  def messagesFromJavaCode(scalaFileText: String, javaFileText: String, javaClassName: String): List[Message] = {
    myFixture.addFileToProject("dummy.scala", scalaFileText)
    val myFile: PsiFile = myFixture.addFileToProject(javaClassName + JavaFileType.DOT_DEFAULT_EXTENSION, javaFileText)
    myFixture.openFileInEditor(myFile.getVirtualFile)
    val allInfo = myFixture.doHighlighting()

    import scala.collection.JavaConverters._
    allInfo.asScala.toList.collect {
      case highlightInfo if highlightInfo.`type`.getSeverity(null) == HighlightSeverity.ERROR =>
        new Error(highlightInfo.getText, highlightInfo.getDescription)
    }
  }

  def messagesFromScalaCode(scalaFileText: String, javaFileText: String): List[Message] = {
    myFixture.addFileToProject("dummy.java", javaFileText)
    myFixture.configureByText("dummy.scala", scalaFileText)
    PsiDocumentManager.getInstance(getProject).commitAllDocuments()

    val mock = new AnnotatorHolderMock
    val annotator = new ScalaAnnotator

    getFile.depthFirst.foreach(annotator.annotate(_, mock))
    mock.annotations.filter {
      case Error(_, null) | Error(null, _) => false
      case Error(_, _) => true
      case _ => false
    }
  }

  def assertMatches[T](actual: T)(pattern: PartialFunction[T, Unit]) {
    Assert.assertTrue("actual: " + actual.toString, pattern.isDefinedAt(actual))
  }

  def assertNoErrors(messages: List[Message]): Unit = {
    assertMatches(messages) {
      case Nil =>
    }
  }

  val CannotResolveMethod = ContainsPattern("Cannot resolve method")
  val CannotBeApplied = ContainsPattern("cannot be applied")

  case class ContainsPattern(fragment: String) {
    def unapply(s: String) = s.contains(fragment)
  }
}

