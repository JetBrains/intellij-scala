package org.jetbrains.plugins.scala.project.migration.store

import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.util.{Failure, Success, Try}

/**
  * User: Dmitry.Naydanov
  * Date: 29.09.16.
  */
object ManifestAttributes {
  trait ManifestAttribute {
    def name: String
    def parse(attrValue: String): Seq[String]
  }
  
  object MigratorFqnAttribute extends ManifestAttribute {
    override def name: String = "Intellij-Migrator-Fqn"
    override def parse(attrValue: String): Seq[String] = Seq(attrValue) //todo multiple migrators ? 
  }
  
  object InspectionPackageAttribute extends ManifestAttribute {
    override def name: String = "Intellij-Inspection-Fqns"
    override def parse(attrValue: String): Seq[String] = Try(parseFqns(attrValue)) match {
      case Success(r) => r
      case Failure(_) => Seq.empty
    }
  }

  
  /**
    * @param fqnsIn input from manifest file like org.azaza.inspections.{MyUsefulInspection, MyStupidInspection}
    * @return parsed fqns like [org.azaza.inspections.MyUsefulInspection, org.azaza.inspections.MyStupidInspection]
    */
  private def parseFqns(fqnsIn: String): Seq[String] = {
    def onError(msg: String) = throw new IllegalArgumentException(msg)
    
    val fqns = fqnsIn.trim.stripSuffix("}")
    val id = fqns.indexOf('{')
    
    if (id == -1) {
      if (ScalaNamesUtil isQualifiedName fqns) return Seq(fqns) else onError(s"Got $fqns which is not a full qualified name")
    } 
    
    fqns.split('{').toList match {
      case qualifier :: List(names) => 
        names.split(',').toSeq.map(n => qualifier + n.trim).filter(fqn => ScalaNamesUtil isQualifiedName fqn)
      case _ => onError(s"Bad { position: $fqns")
    }
  }
}
