package org.jetbrains.sbt.project.structure.data

case class JarDependencyData(file: InterpretablePath, configurations: Seq[Configuration])

object JarDependencyData:
  given PathConstructor[String] => XmlDeserializer[JarDependencyData] = what =>
    val jar = InterpretablePath.construct(what.text)
    val configurations = (what \ "@configurations").headOption.map(n => Configuration.fromString(n.text))
    Right(JarDependencyData(jar, configurations.getOrElse(Seq.empty)))
