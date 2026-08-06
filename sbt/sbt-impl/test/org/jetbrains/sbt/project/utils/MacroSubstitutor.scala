package org.jetbrains.sbt.project.utils

/**
 * The class is intended to be able to use variables in the expected test data, like: {{{
 *   compileOutputPath := "%PROJECT_ROOT%/target/out/jvm/scala-3.3.3/subProject1/classes"
 * }}}
 *
 * ATTENTION: try to not overuse this entity too much.
 * It's better to keep expected test data as clear as possible and not use too many different macro keys in test data.
 */
class MacroSubstitutor(val substitutions: Map[String, String]) {

  def replaceMacroWithValue(actual: String): String = {
    substitutions.foldLeft(actual) { case (actualCurrent, (key, value)) =>
      actualCurrent.replace(key, value)
    }
  }

  def replaceValuesWithMacro(actual: String, expected: String): String = {
    substitutions.foldLeft(actual) { case (actualCurrent, (key, value)) =>
      // for some reason, some expected test data can be null =/
      if (expected != null && expected.contains(key))
        actualCurrent.replace(value, key)
      else
        actualCurrent
    }
  }

  def replaceValuesWithMacro(actualItems: Seq[String], expectedItems: Seq[String]): Seq[String] = {
    // for some reason, some expected test data can be null =/
    val substitutionsWithKeysPresentInExpectedData: Map[String, String] = if (expectedItems.nonEmpty)
      substitutions.filter { case (key, _) => expectedItems.exists(i => i != null && i.contains(key) ) }
    else
      substitutions

    actualItems.zipWithIndex.map { case (actual, _) =>
      substitutionsWithKeysPresentInExpectedData.foldLeft(actual) { case (actualCurrent, (key, value)) =>
        actualCurrent.replace(value, key)
      }
    }
  }

  def containsSomeMacro(value: String): Boolean =
    value != null && substitutions.exists { case (key, _) => value.contains(key) }
}

object MacroSubstitutor {
  object Keys {
    val ProjectRoot = "%PROJECT_ROOT%"
  }
}