package org.jetbrains.jps.incremental.scala.utils

object ScalaJDKIncompatibilityDetector {

  val JdkCompatibilityWarningPrefix: String =
    s"""Incompatible JDK version for Scala.
       |
       |The compiler has encountered an error that is likely caused by incompatible Scala and JDK versions.
       |Please check the official compatibility table: https://docs.scala-lang.org/overviews/jdk-compatibility/overview.html#scala-compatibility-table
       |You may need to update either your Scala or JDK version to resolve this issue.
       |
       |""".stripMargin

  /**
   * Prepend warning information to the given text if it detects a possible Scala/JDK compatibility issue.
   *
   * @return the updated text with a JDK compatibility warning prepended, or the original text if no issues are detected.
   */
  def prependWithWarning(text: String, jdkFeatureVersion: Option[Int]): String =
    if (containsScalaJdkCompatibilityError(text, jdkFeatureVersion)) {
      JdkCompatibilityWarningPrefix + text
    } else {
      text
    }

  /**
   * Determines whether the given error text matches a known Scala/JDK incompatibility error pattern.
   *
   * @param text the error message text to analyze.
   */
  private def containsScalaJdkCompatibilityError(text: String, jdkFeatureVersion: Option[Int]): Boolean = {
    // Error indicating JDK incompatibility with Scala 2.11.x or 2.12.x (e.g., Scala 2.12.0  & JDK 25)
    val case1 = text.contains("scala.reflect.internal.MissingRequirementError: object java.lang.Object in compiler mirror not found") &&
      text.contains("scala.reflect.internal.MissingRequirementError$.signal")

    // Error indicating JDK incompatibility with Scala 2.12.x or 2.13.x (e.g., Scala 2.12.4  & JDK 25)
    val case2 = text.contains("scala.reflect.internal.FatalError") &&
      text.contains("bad constant pool index: 0 at") &&
      text.contains("scala.reflect.internal.Reporting.abort(Reporting.scala")

    // Error indicating JDK incompatibility with Scala 3 (e.g., Scala 3.3.0 & JDK 25)
    val accessFlagPattern = text.contains("error while loading AccessFlag") && text.contains("class file /modules/java.base/java/lang/reflect/AccessFlag.class is broken")
    val elementTypePattern = text.contains("error while loading ElementType") && text.contains("class file /modules/java.base/java/lang/annotation/ElementType.class is broken")
    val case3 = (accessFlagPattern || elementTypePattern) && text.contains("bad constant pool index: 0 at")

    case1 || case2 || case3 || isScala3_8JdkVersionError(text, jdkFeatureVersion)
  }

  /**
   * Checks whether the given text contains a known Scala 3.8 and JDK < 17 compatibility error pattern.
   */
  private def isScala3_8JdkVersionError(text: String, jdkFeatureVersion: Option[Int]): Boolean = {
    // Additional validation to be more sure the Scala/JDK incompatibility note is shown for the right combination
    val isBelow17 = jdkFeatureVersion.exists(_ < 17)
    val regex = """java\.lang\.UnsupportedClassVersionError:.*has been compiled by a more recent version of the Java Runtime \(class file version 61.0\)""".r
    val methodCheck = text.contains("java.lang.ClassLoader.defineClass")
    isBelow17 && regex.findFirstIn(text).isDefined && methodCheck
  }
}