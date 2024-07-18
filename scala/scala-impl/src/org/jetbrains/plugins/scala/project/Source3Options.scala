package org.jetbrains.plugins.scala.project

import org.jetbrains.plugins.scala.project.Source3Options.Names

/**
 * Options for Scala 3 source compatibility alá -Xsource:3, -Xsource:3-cross, -Xsource-features:...
 *
 * See https://docs.scala-lang.org/scala3/guides/migration/tooling-scala2-xsource3.html
 */
case class Source3Options(isSource3Enabled: Boolean,
                          caseApplyCopyAccess: Boolean,
                          caseCompanionFunction: Boolean,
                          caseCopyByName: Boolean,
                          inferOverride: Boolean,
                          any2StringAdd: Boolean,
                          unicodeEscapesRaw: Boolean,
                          stringContextScope: Boolean,
                          leadingInfix: Boolean,
                          packagePrefixImplicits: Boolean,
                          implicitResolution: Boolean,
                       ) {
  def featureNames: Set[String] = {
    def makeSet(tuple: (Boolean, String)*): Set[String] =
      tuple.collect { case (true, name) => name }.toSet

    makeSet(
      caseApplyCopyAccess -> Names.caseApplyCopyAccess,
      caseCompanionFunction -> Names.caseCompanionFunction,
      caseCopyByName -> Names.caseCopyByName,
      inferOverride -> Names.inferOverride,
      any2StringAdd -> Names.any2StringAdd,
      unicodeEscapesRaw -> Names.unicodeEscapesRaw,
      stringContextScope -> Names.stringContextScope,
      leadingInfix -> Names.leadingInfix,
      packagePrefixImplicits -> Names.packagePrefixImplicits,
      implicitResolution -> Names.implicitResolution,
    )
  }
}

object Source3Options {
  val none: Source3Options = Source3Options(
    isSource3Enabled = false,
    caseApplyCopyAccess = false,
    caseCompanionFunction = false,
    caseCopyByName = false,
    inferOverride = false,
    any2StringAdd = false,
    unicodeEscapesRaw = false,
    stringContextScope = false,
    leadingInfix = false,
    packagePrefixImplicits = false,
    implicitResolution = false,
  )

  val all: Source3Options = Source3Options(
    isSource3Enabled = true,
    caseApplyCopyAccess = true,
    caseCompanionFunction = true,
    caseCopyByName = true,
    inferOverride = true,
    any2StringAdd = true,
    unicodeEscapesRaw = true,
    stringContextScope = true,
    leadingInfix = true,
    packagePrefixImplicits = true,
    implicitResolution = true,
  )

  val with_v13_13: Source3Options = fromFeatureSet(Names.v13_13)
  val with_v13_14: Source3Options = fromFeatureSet(Names.v13_14)

  def fromAdditionalCompilerFlags(flags: collection.Set[String]): Source3Options = {
    // note: -Xsource:3-cross == -Xsource:3 -Xsource-features:_
    val hasSource3cross = flags.contains("-Xsource:3-cross")
    val isSource3 = hasSource3cross || flags.contains("-Xsource:3")

    /*
       For -Xsource-features we have the following rules:
       - if -Xsource:3-cross is enabled, all features are enabled and we don't need to parse any -Xsource-features
       - groups like _ or v2.13.14 add all/multiple features
       - negative features like "-any2stringadd" remove features only from features added by groups
       - explicitly named features are always used and cannot be removed with negative features
         (a.k.a. "-Xsource-features:any2stringadd,-any2stringadd" is the same as "-Xsource-features:any2stringadd")
       - multiple -Xsource-features arguments will be merged
         (a.k.a.
           "-Xsource-features:any2stringadd -Xsource-features:leading-infix" is the same as
           "-Xsource-features:any2stringadd,leading-infix")
     */
    val XSourceFeaturePrefix = "-Xsource-features:"
    lazy val featureFlags =
      flags
        .iterator
        .filter(_.startsWith(XSourceFeaturePrefix))
        .map(_.stripPrefix(XSourceFeaturePrefix))
        .map(_.split(',').toSeq)
        .flatMap { args =>
          val (groupArgs, nonGroupArgs) = args.partition(Names.byVersion.contains)
          val byGroups = groupArgs.iterator.flatMap(Names.byVersion).toSet
          val neg = nonGroupArgs.filter(_.startsWith("-")).map(_.stripPrefix("-"))
          val pos = nonGroupArgs.filterNot(_.startsWith("-"))
          byGroups -- neg ++ pos
        }
        .toSet

    def hasFeature(flag: String): Boolean =
      hasSource3cross || featureFlags.contains(flag)

    fromFeatureSet(hasFeature, isSource3Enabled = isSource3)
  }

  def fromFeatureSet(hasFeature: String => Boolean, isSource3Enabled: Boolean = true): Source3Options = {
    Source3Options(
      isSource3Enabled = isSource3Enabled,
      caseApplyCopyAccess = hasFeature(Names.caseApplyCopyAccess),
      caseCompanionFunction = hasFeature(Names.caseCompanionFunction),
      caseCopyByName = hasFeature(Names.caseCopyByName),
      inferOverride = hasFeature(Names.inferOverride),
      any2StringAdd = hasFeature(Names.any2StringAdd),
      unicodeEscapesRaw = hasFeature(Names.unicodeEscapesRaw),
      stringContextScope = hasFeature(Names.stringContextScope),
      leadingInfix = hasFeature(Names.leadingInfix),
      packagePrefixImplicits = hasFeature(Names.packagePrefixImplicits),
      implicitResolution = hasFeature(Names.implicitResolution),
    )
  }

  object Names {
    // best to look here: https://github.com/scala/scala/blob/2.13.x/src/compiler/scala/tools/nsc/settings/ScalaSettings.scala#L170
    // Changes affecting binary encoding
    val caseApplyCopyAccess = "case-apply-copy-access"
    val caseCompanionFunction = "case-companion-function"
    val caseCopyByName = "case-copy-by-name"
    val inferOverride = "infer-override"

    // Other semantic changes
    val any2StringAdd = "any2stringadd"
    val unicodeEscapesRaw = "unicode-escapes-raw"
    val stringContextScope = "string-context-scope"
    val leadingInfix = "leading-infix"
    val packagePrefixImplicits = "package-prefix-implicits"
    val implicitResolution = "implicit-resolution"

    val v13_13: Set[String] = Set(caseApplyCopyAccess, caseCompanionFunction, inferOverride, any2StringAdd, unicodeEscapesRaw, stringContextScope, leadingInfix, packagePrefixImplicits)
    val v13_14: Set[String] = Set(implicitResolution) ++ v13_13

    val all: Set[String] = Set(
      caseApplyCopyAccess,
      caseCompanionFunction,
      caseCopyByName,
      inferOverride,

      any2StringAdd,
      unicodeEscapesRaw,
      stringContextScope,
      leadingInfix,
      packagePrefixImplicits,
      implicitResolution
    )

    val byVersion: Map[String, Set[String]] = Map(
      "13.13" -> v13_13,
      "13.14" -> v13_14,
      "_" -> all,
    )
  }
}