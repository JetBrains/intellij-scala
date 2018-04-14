package org.jetbrains.bsp

import java.io.File

import com.intellij.openapi.externalSystem.model.{Key, ProjectKeys}
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.project.data.SbtEntityData

@SerialVersionUID(1)
case class ScalaSdkData(
    scalaOrganization: String,
    scalaVersion: Option[Version],
    scalacClasspath: Seq[File],
    scalacOptions: Seq[String],
    jdk: Option[SdkReference],
    javacOptions: Seq[String]
) extends AbstractExternalEntityData(bsp.ProjectSystemId)
    with Product {
  override def hashCode(): Int = runtime.ScalaRunTime._hashCode(this)
  override def equals(obj: scala.Any): Boolean = obj match {
    case data: SbtEntityData =>
      //noinspection CorrespondsUnsorted
      this.canEqual(data) &&
        (this.productIterator sameElements data.productIterator)
    case _ => false
  }
}

object ScalaSdkData {
  def datakey[T](
      clazz: Class[T],
      weight: Int = ProjectKeys.MODULE.getProcessingWeight + 1
  ): Key[T] = new Key(clazz.getName, weight)

  val Key: Key[ScalaSdkData] = {
    datakey(classOf[ScalaSdkData],
            ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight + 1)
  }
}
