package org.jetbrains.plugins.scala.lang.psi.api.statements

/**
 * This trait represents two possible enum cases:
 *  1. Class cases - cases that are parameterized, either with a type parameter section [...] or with one or more (possibly empty) parameter sections
 *  1. Singleton cases - all other cases
 *
 * @note There is also a concept of "enum case category", which has 3 possible values": Class, Simple and Value
 *       Simple cases and Value cases are collectively called Singleton cases.
 *       Right now we are not interested in case "category", but if in future we need it we can introduce a new ADT,
 *       or even replace `ScEnumCaseKind` with `ScEnumCaseCategory`
 * @see https://docs.scala-lang.org/scala3/reference/enums/desugarEnums.html
 */
sealed trait ScEnumCaseKind
object ScEnumCaseKind {
  final object SingletonCase extends ScEnumCaseKind
  final object ClassCase extends ScEnumCaseKind
}
