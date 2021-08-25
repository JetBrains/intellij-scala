package org.jetbrains.sbt.language.utils

import com.intellij.openapi.util.Key
import org.jetbrains.plugins.scala.LatestScalaVersions.{Scala_2_11, Scala_2_12, Scala_2_13, Scala_3_0}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr, ScReferenceExpression}

object SbtScalacOptionUtils {
  val SCALAC_OPTIONS = "scalacOptions"

  val SCALAC_OPTIONS_DOC_KEY: Key[String] = Key.create("SCALAC_OPTION_DOC")

  def matchesScalacOptions(expr: ScExpression): Boolean = expr match {
    case ref: ScReferenceExpression => ref.refName == SCALAC_OPTIONS
    // e.g.: ThisBuild / scalacOptions
    case ScInfixExpr(_, op, right: ScReferenceExpression) =>
      op.refName == "/" && right.refName == SCALAC_OPTIONS
    case _ => false
  }

  // TODO: Support advanced options
  val STANDARD_SCALAC_OPTIONS = Seq(
    SbtScalacOptionInfo("-Dproperty=value", "Pass -Dproperty=value directly to the runtime system.", Set(Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-J<flag>", "Pass <flag> directly to the runtime system.", Set(Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-P:<plugin>:<opt>", "Pass an option to a plugin.", Set(Scala_3_0, Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-V", "Print a synopsis of verbose options.", Set(Scala_2_13)),
    SbtScalacOptionInfo("-W", "Print a synopsis of warning options.", Set(Scala_2_13)),
    SbtScalacOptionInfo("-Wconf:<patterns>", "Configure reporting of compiler warnings; use `help` for details.", Set(Scala_2_12)),
    SbtScalacOptionInfo("-Werror", "Fail the compilation if there are any warnings.", Set(Scala_2_13)),
    SbtScalacOptionInfo("-X", "Print a synopsis of advanced options.", Set(Scala_3_0, Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-Y", "Print a synopsis of private options.", Set(Scala_3_0, Scala_2_13)),
    SbtScalacOptionInfo("-bootclasspath", "Override location of bootstrap class files.", Set(Scala_3_0, Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-classpath", "Specify where to find user class files.", Set(Scala_3_0, Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-d", "Destination for generated classfiles.", Set(Scala_3_0, Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-dependencyfile", "Set dependency tracking file.", Set(Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-deprecation", "Emit warning and location for usages of deprecated APIs.", Set(Scala_3_0, Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-encoding", "Specify character encoding used by source files.", Set(Scala_3_0, Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-explain", "Explain errors in more detail.", Set(Scala_3_0)),
    SbtScalacOptionInfo("-explain-types", "Explain type errors in more detail (deprecated, use -explain instead).", Set(Scala_3_0)),
    SbtScalacOptionInfo("-explaintypes", "Explain type errors in more detail.", Set(Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-extdirs", "Override location of installed extensions.", Set(Scala_3_0, Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-feature", "Emit warning and location for usages of features that should be imported explicitly.", Set(Scala_3_0, Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-from-tasty", "Compile classes from tasty files. The arguments are .tasty or .jar files.", Set(Scala_3_0)),
    SbtScalacOptionInfo("-g:<level>", "Set level of generated debugging info. (none,source,line,[vars],notailcalls)", Set(Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-help", "Print a synopsis of standard options.", Set(Scala_3_0, Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-indent", "Together with -rewrite, remove {...} syntax when possible due to significant indentation.", Set(Scala_3_0)),
    SbtScalacOptionInfo("-javabootclasspath", "Override java boot classpath.", Set(Scala_3_0, Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-javaextdirs", "Override java extdirs classpath.", Set(Scala_3_0, Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-language:<features>", "Enable one or more language features.", Set(Scala_3_0, Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-new-syntax", "Require `then` and `do` in control expressions.", Set(Scala_3_0)),
    SbtScalacOptionInfo("-no-indent", "Require classical {...} syntax, indentation is not significant.", Set(Scala_3_0)),
    SbtScalacOptionInfo("-no-specialization", "Ignore @specialize annotations.", Set(Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-nobootcp", "Do not use the boot classpath for the scala jars.", Set(Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-nowarn", "Silence all warnings.", Set(Scala_3_0, Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-old-syntax", "Require `(...)` around conditions.", Set(Scala_3_0)),
    SbtScalacOptionInfo("-opt:<optimizations>", "Enable optimizations, `help` for details.", Set(Scala_2_13, Scala_2_12)),
    SbtScalacOptionInfo("-opt-inline-from:<patterns>", "Patterns for classfile names from which to allow inlining, `help` for details.", Set(Scala_2_13, Scala_2_12)),
    SbtScalacOptionInfo("-opt-warnings:<warnings>", "Enable optimizer warnings, `help` for details.", Set(Scala_2_13, Scala_2_12)),
    SbtScalacOptionInfo("-optimise", "Generates faster bytecode by applying optimisations to the program", Set(Scala_2_11)),
    SbtScalacOptionInfo("-pagewidth", "Set page width\nDefault: 222.", Set(Scala_3_0)),
    SbtScalacOptionInfo("-print-lines", "Show source code line numbers.", Set(Scala_3_0)),
    SbtScalacOptionInfo("-print-tasty", "Prints the raw tasty.", Set(Scala_3_0)),
    SbtScalacOptionInfo("-print", "Print program with Scala-specific features removed.", Set(Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-project-url", "The source repository of your project.", Set(Scala_3_0)),
    SbtScalacOptionInfo("-release", "Compile code with classes specific to the given version of the Java platform available on the classpath and emit bytecode for this version.", Set(Scala_3_0, Scala_2_13, Scala_2_12)),
    SbtScalacOptionInfo("-rewrite", "When used in conjunction with a `...-migration` source version, rewrites sources to migrate to new version.", Set(Scala_3_0)),
    SbtScalacOptionInfo("-rootdir", "The absolute path of the project root directory, usually the git/scm checkout. Used by -Wconf.", Set(Scala_2_13, Scala_2_12)),
    SbtScalacOptionInfo("-scalajs", "Compile in Scala.js mode (requires scalajs-library.jar on the classpath).", Set(Scala_3_0)),
    SbtScalacOptionInfo("-scalajs-genStaticForwardersForNonTopLevelObjects", "Generate static forwarders even for non-top-level objects (Scala.js only)", Set(Scala_3_0)),
    SbtScalacOptionInfo("-scalajs-mapSourceURI", "rebases source URIs from uri1 to uri2 (or to a relative URI) for source maps (Scala.js only)", Set(Scala_3_0)),
    SbtScalacOptionInfo("-semanticdb-target", "Specify an alternative output directory for SemanticDB files.", Set(Scala_3_0)),
    SbtScalacOptionInfo("-source", "source version\nDefault: 3.0.\nChoices: 3.0, future, 3.0-migration, future-migration.", Set(Scala_3_0)),
    SbtScalacOptionInfo("-sourcepath", "Specify location(s) of source files.", Set(Scala_3_0, Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-sourceroot", "Specify workspace root directory.\nDefault: ..", Set(Scala_3_0)),
    SbtScalacOptionInfo("-target:<target>", "Target platform for object files.", Set(Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-toolcp", "Add to the runner classpath.", Set(Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-unchecked", "Enable additional warnings where generated code depends on assumptions.", Set(Scala_3_0, Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-uniqid", "Uniquely tag all identifiers in debugging output.", Set(Scala_3_0, Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-usejavacp", "Utilize the java.class.path in classpath resolution.", Set(Scala_3_0, Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-usemanifestcp", "Utilize the manifest in classpath resolution.", Set(Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-verbose", "Output messages about what the compiler is doing.", Set(Scala_3_0, Scala_2_13, Scala_2_12, Scala_2_11)),
    SbtScalacOptionInfo("-version", "Print product version and exit.", Set(Scala_3_0, Scala_2_13, Scala_2_12, Scala_2_11)),
  )

  // TODO: Get options using the Compile Server
  def getScalacOptions: Seq[SbtScalacOptionInfo] = STANDARD_SCALAC_OPTIONS
}
