package scala.meta

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector

class MetaAnnotationInjector extends SyntheticMembersInjector {
  override def injectMembers(source: ScTypeDefinition): Seq[String] = {
    if (source.annotations.exists(_.isMetaAnnotation))
      Seq(
      "def fooBarMeta: String = ???",
      "class MetaTestClass",
      "type MetaTypeTest = Int"
      )
    else Seq.empty
  }
}
