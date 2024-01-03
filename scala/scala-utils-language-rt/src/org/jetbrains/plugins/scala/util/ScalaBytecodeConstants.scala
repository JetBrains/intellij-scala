package org.jetbrains.plugins.scala.util

/**
 * Contains constants representing internal JVM representation of scala entities
 */
object ScalaBytecodeConstants {
  /**
   * In JVM, Scala package object {{{
   *   package example
   *
   *   package object inner {
   *     def foo: String
   *   }
   * }}}
   * will be represented by two special classes {{{
   *  public final class package {
   *      public static String foo() {
   *          return package$.MODULE$.foo();
   *      }
   *  }
   *
   *  public final class package$ {
   *      public static final package$ MODULE$ = new package$();
   *      public String foo() {
   *          return "42";
   *      }
   *  }
   * }}}
   */
  @inline final val PackageObjectClassName = "package"
  @inline final val PackageObjectClassPackageSuffix = ".package"

  @inline final val PackageObjectSingletonClassName = "package$"
  @inline final val PackageObjectSingletonClassPackageSuffix = ".package$"

  /**
   * In Scala 3 top-level non-scala definitions are located in a synthetic class.
   * If a file name is `definitions.scala` then there will be two classes generated: {{{
   *   package org.example;
   *
   *   public final class definitions$package {
   *       // Static forwarders, like:
   *       public static String topLevelFoo() {
   *           return definitions$package$.MODULE$.topLevelFoo();
   *       }
   *       ...
   *   }
   *
   *   public final class definitions$package$ implements Serializable {
   *       public static final definitions$package$ MODULE$ = new definitions$package$();
   *       ...
   *       public String topLevelFoo() {
   *           return "23";
   *       }
   *       ...
   *   }
   * }}}
   */
  @inline final val TopLevelDefinitionsClassNameSuffix = "$package"
  @inline final val TopLevelDefinitionsSingletonClassNameSuffix = "$package$"


  /**
   * The suffix used by scala compiler <= 2.11
   * for the generated "trait implementation class" in the bytecode representation.
   *
   * In Scala 2.11 and earlier, when a trait was compiled, it produced two JVM
   * entities: an interface, and a class that held the implementation of all
   * concrete trait methods. This "trait implementation class" was given the name
   * of the trait with `"$class"` appended.
   * ==Example==
   * Original Class: {{{
   *   trait MyTrait {
   *     def concreteMethod(): String = ???
   *     def abstractMethod(): String
   *   }
   * }}}
   * Decompiled Java Class: {{{
   *   public interface MyTrait {
   *       String concreteMethod();
   *       String abstractMethod();
   *   }
   *
   *   public abstract class MyTrait$class {
   *       public static String concreteMethod(MyTrait $this) {
   *           throw .MODULE$.$qmark$qmark$qmark();
   *       }
   *       public static void $init$(MyTrait $this) {}
   *   }
   * }}}
   *
   * Note: This mechanism was deprecated from Scala 2.12 onwards, with traits
   * directly compiling to interfaces using Java 8's default method functionality.<br>
   * See https://github.com/scala/scala/releases/v2.12.0 {{{
   *    The compiler no longer generates trait implementation classes (T$class.class) and anonymous function classes (C$$anonfun$1.class).
   * }}}
   */
  @inline final val TraitImplementationClassSuffix_211 = "$class"
}
