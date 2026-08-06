package org.jetbrains.plugins.scala.conversion

class JavaToScalaConversionCodeTest extends JavaToScalaConversionTestBase {

  def testAnnotations(): Unit = {
    myFixture.addClass(
      """package org.jetbrains.annotations;
        |
        |import java.lang.annotation.*;
        |
        |@Retention(RetentionPolicy.CLASS)
        |@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE})
        |public @interface NotNull {}
        |""".stripMargin
    )

    myFixture.addClass(
      """package org.jetbrains.annotations;
        |
        |import java.lang.annotation.*;
        |
        |@Retention(RetentionPolicy.CLASS)
        |@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE})
        |public @interface Nullable {}
        |""".stripMargin
    )

    myFixture.addClass(
      """package org.jetbrains.annotations;
        |
        |import java.lang.annotation.*;
        |
        |@Retention(RetentionPolicy.CLASS)
        |@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE})
        |public @interface NonNls {}
        |""".stripMargin
    )

    myFixture.addClass(
      """package org.jetbrains.annotations;
        |
        |import java.lang.annotation.*;
        |
        |@Retention(RetentionPolicy.CLASS)
        |@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE})
        |public @interface Nls {}
        |""".stripMargin
    )

    doTest(
      """import org.jetbrains.annotations.Nls;
        |import org.jetbrains.annotations.NonNls;
        |import org.jetbrains.annotations.NotNull;
        |import org.jetbrains.annotations.Nullable;
        |
        |public class Example {
        |    @Override public String toString() {return super.toString();}
        |    @Deprecated public String foo1() { return null; }
        |    @NotNull public String foo2() { return null; }
        |    @Nullable public String foo3() { return null; }
        |    @NonNls public String foo4() { throw new UnsupportedOperationException(); }
        |    @Nls public String foo5() { throw new UnsupportedOperationException(); }
        |    @SuppressWarnings("test")
        |    public String foo6() { throw new UnsupportedOperationException(); }
        |}""".stripMargin,
      """import org.jetbrains.annotations.Nls
        |import org.jetbrains.annotations.NonNls
        |import org.jetbrains.annotations.NotNull
        |import org.jetbrains.annotations.Nullable
        |
        |class Example {
        |  override def toString: String = super.toString
        |
        |  @deprecated def foo1: String = null
        |
        |  @NotNull def foo2: String = null
        |
        |  @Nullable def foo3: String = null
        |
        |  @NonNls def foo4: String = throw new UnsupportedOperationException
        |
        |  @Nls def foo5: String = throw new UnsupportedOperationException
        |
        |  @SuppressWarnings(Array("test")) def foo6 = throw new UnsupportedOperationException
        |}""".stripMargin
    )
  }
}
