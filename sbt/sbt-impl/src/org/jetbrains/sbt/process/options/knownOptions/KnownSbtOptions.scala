package org.jetbrains.sbt.process.options.knownOptions

import org.jetbrains.sbt.process.options.knownOptions.KnownSbtOption.{Form, Spelling}
import org.jetbrains.sbt.process.options.knownOptions.KnownSbtOptionArgMapping.MappedArguments

import scala.collection.immutable.ListMap

/**
 * Registry of sbt launcher options understood by the IDE
 *
 * @see [[SbtProcessOptionsResolver]] for how registry entries are used
 */
private[options] object KnownSbtOptions {

  private val Entries: Seq[KnownSbtOption] = Seq(
    allJvmSeparateValueOption(name = "-sbt-boot", valueName = "path") { value =>
      Seq(s"-Dsbt.boot.directory=$value")
    },
    allJvmSeparateValueOption(name = "-sbt-dir", valueName = "path") { value =>
      Seq(s"-Dsbt.global.base=$value")
    },
    allJvmSeparateValueOption(name = "-ivy", valueName = "path") { value =>
      Seq(s"-Dsbt.ivy.home=$value")
    },
    allJvmContextOption(name = "-no-global") { projectPath =>
      Seq(s"-Dsbt.global.base=$projectPath/project/.sbtboot")
    },
    allJvmContextOption(name = "-no-share") { _ =>
      Seq(
        "-Dsbt.global.base=project/.sbtboot",
        "-Dsbt.boot.directory=project/.boot",
        "-Dsbt.ivy.home=project/.ivy"
      )
    },
    allJvmSeparateValueOption(name = "-jvm-debug", valueName = "port") { value =>
      Seq(s"-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$value")
    },
    allJvmSeparateValueOption(name = "-sbt-cache", valueName = "path") { value =>
      Seq(s"-Dsbt.global.localcache=$value")
    },
    allJvmFixedOption(name = "-debug-inc")(Seq("-Dxsbt.inc.debug=true")),
    allJvmFixedOption(name = "-traces")(Seq("-Dsbt.traces=true")),
    allJvmFixedOption(name = "-timings")(
      Seq(
        "-Dsbt.task.timings=true",
        "-Dsbt.task.timings.on.shutdown=true"
      )
    ),
    shellJvmFixedOption(name = "-no-colors")(Seq("-Dsbt.log.noformat=true")),
    shellJvmInlineValueOption(name = "-color", valueDescription = "auto|always|true|false|never") { value =>
      Seq(s"-Dsbt.color=$value")
    },
    launcherFixedOption(name = "-error")(Seq("--error")),
    launcherFixedOption(name = "-warn")(Seq("--warn")),
    launcherFixedOption(name = "-info")(Seq("--info")),
    // Unlike -error/-warn/-info, -debug also has the sbt short alias -d.
    entry(
      spelling = flagSpelling(name = "-debug"),
      aliases = Seq(spelling("-d", Form.NoValue, "-d"))
    )((_, _) => MappedArguments.launcher(Seq("--debug"))),
  )

  /** Lookup from accepted option text to its owning entry and spelling metadata. */
  val AllSpellings: ListMap[String, (KnownSbtOption, Spelling)] =
    ListMap.from {
      Entries.flatMap { entry =>
        entry.allSpellings.map(spelling => spelling.text -> (entry, spelling))
      }
    }

  /** Diagnostic helper text for every accepted spelling, in registry order. */
  val AllHelperMessages: Seq[String] =
    AllSpellings.values.map { case (_, spelling) => spelling.helperMsg }.toSeq

  /** Finds a spelling by exact normalized option text. */
  def findExactSpelling(text: String): Option[(KnownSbtOption, Spelling)] =
    AllSpellings.get(text)

  /** Finds a known spelling in a raw parsed option occurrence, including value-bearing forms. */
  def findMatchingSpelling(rawOption: String): Option[(KnownSbtOption, Spelling)] =
    findExactNoValueSpelling(rawOption)
      .orElse(findExactSeparateValueSpelling(rawOption))
      .orElse(findExactInlineValueSpelling(rawOption))

  private def findExactNoValueSpelling(
    rawOption: String
  ): Option[(KnownSbtOption, Spelling)] =
    AllSpellings.get(rawOption).filter { case (_, spelling) =>
      spelling.valueForm == Form.NoValue
    }

  private def findExactSeparateValueSpelling(
    rawOption: String
  ): Option[(KnownSbtOption, Spelling)] = {
    val whitespaceIndex = rawOption.indexWhere(_.isWhitespace)
    if (whitespaceIndex > 0) {
      val optionKey = rawOption.substring(0, whitespaceIndex)
      AllSpellings.get(optionKey).filter { case (_, spelling) =>
        spelling.valueForm == Form.SeparateValue
      }
    } else {
      None
    }
  }

  private def findExactInlineValueSpelling(
    rawOption: String
  ): Option[(KnownSbtOption, Spelling)] = {
    val separatorIndex = rawOption.indexOf('=')
    if (separatorIndex >= 0) {
      val optionKey = rawOption.substring(0, separatorIndex + 1)
      AllSpellings.get(optionKey).filter { case (_, spelling) =>
        spelling.valueForm == Form.InlineValue
      }
    } else {
      None
    }
  }

  private def allJvmSeparateValueOption(
    name: String,
    valueName: String
  )(toVmOptions: String => Seq[String]): KnownSbtOption =
    entry(separateValueSpelling(name, valueName)) { (value, _) =>
      MappedArguments.allJvm(toVmOptions(value.get))
    }

  private def shellJvmInlineValueOption(
    name: String,
    valueDescription: String
  )(toVmOptions: String => Seq[String]): KnownSbtOption =
    entry(inlineValueSpelling(name, valueDescription)) { (value, _) =>
      MappedArguments.shellJvm(toVmOptions(value.get))
    }

  private def allJvmContextOption(
    name: String
  )(toVmOptions: String => Seq[String]): KnownSbtOption =
    entry(flagSpelling(name)) { (_, context) =>
      MappedArguments.allJvm(toVmOptions(context))
    }

  private def allJvmFixedOption(
    name: String
  )(vmOptions: Seq[String]): KnownSbtOption =
    entry(flagSpelling(name)) { (_, _) =>
      MappedArguments.allJvm(vmOptions)
    }

  private def shellJvmFixedOption(
    name: String
  )(vmOptions: Seq[String]): KnownSbtOption =
    entry(flagSpelling(name)) { (_, _) =>
      MappedArguments.shellJvm(vmOptions)
    }

  private def launcherFixedOption(
    name: String
  )(args: Seq[String]): KnownSbtOption =
    entry(flagSpelling(name)) { (_, _) =>
      MappedArguments.launcher(args)
    }

  private def entry(
    spelling: Spelling,
    aliases: Seq[Spelling] = Seq.empty
  )(argMapping: KnownSbtOptionArgMapping): KnownSbtOption =
    KnownSbtOption(spelling, argMapping, aliases)

  private def flagSpelling(name: String): Spelling =
    spelling(name, Form.NoValue, name)

  private def separateValueSpelling(name: String, valueName: String): Spelling =
    spelling(name, Form.SeparateValue, s"$name <$valueName>")

  private def inlineValueSpelling(name: String, valueDescription: String): Spelling =
    spelling(s"$name=", Form.InlineValue, s"$name=$valueDescription")

  private def spelling(text: String, valueForm: Form, helperMsg: String): Spelling =
    Spelling(text, valueForm, helperMsg)
}
