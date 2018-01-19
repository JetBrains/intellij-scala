package org.jetbrains.plugins.scala.findUsages.compilerReferences

import org.apache.bcel.classfile.ConstantPool

/**
  * @param fqn          Fully quialified name of a class.
  * @param superClasses Fully qualified names of all super classes/interfaces.
  */
private final case class ClassInfo(
  fqn: String,
  superClasses: Seq[String]
)

private final case class ParsedClassfile(
  classInfo: ClassInfo,
  cp: ConstantPool
)
