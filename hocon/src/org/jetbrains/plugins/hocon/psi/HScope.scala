package org.jetbrains.plugins.hocon.psi

/**
  * "Something that has keys in it and stuff associated with those keys".
  */
trait HScope {
  def directKeyedFields: Iterator[HKeyedField]

  def directSubScopes(key: String): Iterator[HScope] =
    directKeyedFields.filter(_.validKey.map(_.stringValue).contains(key)).flatMap(_.subScopes)
}
