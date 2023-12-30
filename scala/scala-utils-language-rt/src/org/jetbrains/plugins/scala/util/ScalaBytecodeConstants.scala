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
  @inline final val PackageObjectSingletonClassName = "package$"

  @inline final val PackageObjectClassPackageSuffix = ".package"
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
}
