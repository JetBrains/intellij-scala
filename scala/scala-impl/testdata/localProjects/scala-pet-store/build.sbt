organization    := "io.github.pauljamescleary"
name            := "scala-pet-store"
version         := "0.0.1-SNAPSHOT"
scalaVersion    := "2.12.3"

resolvers += Resolver.sonatypeRepo("snapshots")

val CatsVersion       = "1.0.0-RC1"
val CirceVersion      = "0.9.0-M2"
val DoobieVersion     = "0.5.0-M9"
val H2Version         = "1.4.196"
val Http4sVersion     = "0.18.0-M5"
val LogbackVersion    = "1.2.3"
val ScalaCheckVersion = "1.13.5"
val ScalaTestVersion  = "3.0.4"
val FlywayVersion     = "4.2.0"
val PureConfigVersion = "0.8.0"

libraryDependencies ++= Seq(
  "org.typelevel"         %% "cats-core"            % CatsVersion,
  "io.circe"              %% "circe-generic"        % CirceVersion,
  "io.circe"              %% "circe-literal"        % CirceVersion,
  "io.circe"              %% "circe-generic-extras" % CirceVersion,
  "io.circe"              %% "circe-optics"         % CirceVersion,
  "io.circe"              %% "circe-parser"         % CirceVersion,
  "io.circe"              %% "circe-java8"          % CirceVersion,
  "org.tpolecat"          %% "doobie-core"          % DoobieVersion,
  "org.tpolecat"          %% "doobie-h2"            % DoobieVersion,
  "org.tpolecat"          %% "doobie-scalatest"     % DoobieVersion,
  "org.tpolecat"          %% "doobie-hikari"        % DoobieVersion,
  "com.h2database"        %  "h2"                   % H2Version,
  "org.http4s"            %% "http4s-blaze-server"  % Http4sVersion,
  "org.http4s"            %% "http4s-circe"         % Http4sVersion,
  "org.http4s"            %% "http4s-dsl"           % Http4sVersion,
  "ch.qos.logback"        %  "logback-classic"      % LogbackVersion,
  "org.flywaydb"          %  "flyway-core"          % FlywayVersion,
  "com.github.pureconfig" %% "pureconfig"           % PureConfigVersion,
  "org.scalacheck"        %% "scalacheck"           % ScalaCheckVersion % Test,
  "org.scalatest"         %% "scalatest"            % ScalaTestVersion  % Test
)


scalacOptions ++= Seq(
  // format: off
  "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
  "-encoding", "utf-8",                // Specify character encoding used by source files.
  "-explaintypes",                     // Explain type errors in more detail.
  "-feature",                          // Emit warning and location for usages of features that should be imported explicitly.
  "-language:existentials",            // Existential types (besides wildcard types) can be written and inferred
  "-language:experimental.macros",     // Allow macro definition (besides implementation and application)
  "-language:higherKinds",             // Allow higher-kinded types
  "-language:implicitConversions",     // Allow definition of implicit functions called views
  "-unchecked",                        // Enable additional warnings where generated code depends on assumptions.
  "-Xcheckinit",                       // Wrap field accessors to throw an exception on uninitialized access.
  "-Xfatal-warnings",                  // Fail the compilation if there are any warnings.
  "-Xfuture",                          // Turn on future language features.
  "-Xlint:adapted-args",               // Warn if an argument list is modified to match the receiver.
  "-Xlint:by-name-right-associative",  // By-name parameter of right associative operator.
  "-Xlint:constant",                   // Evaluation of a constant arithmetic expression results in an error.
  "-Xlint:delayedinit-select",         // Selecting member of DelayedInit.
  "-Xlint:doc-detached",               // A Scaladoc comment appears to be detached from its element.
  "-Xlint:inaccessible",               // Warn about inaccessible types in method signatures.
  "-Xlint:infer-any",                  // Warn when a type argument is inferred to be `Any`.
  "-Xlint:missing-interpolator",       // A string literal appears to be missing an interpolator id.
  "-Xlint:nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Xlint:nullary-unit",               // Warn when nullary methods return Unit.
  "-Xlint:option-implicit",            // Option.apply used implicit view.
  "-Xlint:package-object-classes",     // Class or object defined in package object.
  "-Xlint:poly-implicit-overload",     // Parameterized overloaded implicit methods are not visible as view bounds.
  "-Xlint:private-shadow",             // A private field (or class parameter) shadows a superclass field.
  "-Xlint:stars-align",                // Pattern sequence wildcard must align with sequence component.
  "-Xlint:type-parameter-shadow",      // A local type parameter shadows a type already in scope.
  "-Xlint:unsound-match",              // Pattern match may not be typesafe.
  "-Yno-adapted-args",                 // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
  "-Ypartial-unification",             // Enable partial unification in type constructor inference
  "-Ywarn-dead-code",                  // Warn when dead code is identified.
  "-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined.
  "-Ywarn-inaccessible",               // Warn about inaccessible types in method signatures.
  "-Ywarn-infer-any",                  // Warn when a type argument is inferred to be `Any`.
  "-Ywarn-nullary-unit",               // Warn when nullary methods return Unit.
  "-Ywarn-nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Ywarn-numeric-widen",              // Warn when numerics are widened.
  "-Ywarn-unused:implicits",           // Warn if an implicit parameter is unused.
  "-Ywarn-unused:imports",             // Warn if an import selector is not referenced.
  "-Ywarn-unused:locals",              // Warn if a local definition is unused.
  // "-Ywarn-unused:params",           // Warn if a value parameter is unused.
  "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused.
  "-Ywarn-unused:privates",            // Warn if a private member is unused.
  "-Ywarn-value-discard"               // Warn when non-Unit expression results are unused.
  // format: on
)

enablePlugins(JavaAppPackaging)
