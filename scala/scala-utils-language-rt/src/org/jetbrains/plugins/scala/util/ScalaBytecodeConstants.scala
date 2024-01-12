package org.jetbrains.plugins.scala.util

/**
 * Contains constants representing internal JVM representation of scala entities
 */
object ScalaBytecodeConstants {

  /**
   * The name of object singleton instance:<br>
   * `public static final MyObject$ MODULE$ = new MyObject$();`
   *
   * ===Details===
   * When Scala compiles an object, it creates two JVM classes to represent it:
   *  1. Class with suffix `$`, which holds all the original declarations.<br>
   *     Inside this class it creates a static field named MODULE$ to hold the singleton instance of that object.
   *  1. Class with static forwarders, which are needed to use the methods from java without referencing cryptic `MyObject$.MODULE$`
   *
   * ===Example===
   * Scala Object:
   * {{{
   *   object MyObject {
   *     def foo: String = null
   *   }
   * }}}
   * Decompiled Java classes:
   * {{{
   *   public final class MyObject$ {
   *       public static final MyObject$ MODULE$ = new MyObject$();
   *
   *       private MyObject$() {}
   *
   *       public String foo() { return null; }
   *   }
   *
   *   public final class MyObject {
   *       public static String foo() {
   *           return MyObject$.MODULE$.foo();
   *       }
   *   }
   * }}}
   *
   * @see [[scala.reflect.NameTransformer.MODULE_INSTANCE_NAME]]
   */
  @inline final val ObjectSingletonInstanceName = "MODULE$"

  /**
   * Package objects are represented as ordinary object with special name "package".<br>
   * See [[ObjectSingletonInstanceName]] for the details
   */
  @inline final val PackageObjectClassName = "package"

  /**
   * Package objects are represented as ordinary object with special name "package".<br>
   * See [[ObjectSingletonInstanceName]] for the details
   */
  @inline final val PackageObjectClassPackageSuffix = ".package"

  /**
   * Package objects are represented as ordinary object with special name "package".<br>
   * See [[ObjectSingletonInstanceName]] for the details
   */
  @inline final val PackageObjectSingletonClassName = "package$"

  /**
   * Package objects are represented as ordinary object with special name "package".<br>
   * See [[ObjectSingletonInstanceName]] for the details
   */
  @inline final val PackageObjectSingletonClassPackageSuffix = ".package$"

  /**
   * In Scala 3 top-level non-scala definitions are added to a special synthetic scala object.
   * The name of the object is `fileName + "$package"`.<br>
   * In JVM the object is represented according to the common rules.<br>
   * See [[ObjectSingletonInstanceName]] for the details
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
