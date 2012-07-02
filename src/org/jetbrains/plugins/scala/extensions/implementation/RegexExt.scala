package org.jetbrains.plugins.scala
package extensions.implementation

import scala.util.matching.Regex

/**
 * Pavel Fatin
 */

class RegexExt(regex: Regex) {
  def matches(s: String) = regex.pattern.matcher(s).matches
}
