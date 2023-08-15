package org.jetbrains.plugins.scala.packagesearch.model

import spray.json.DefaultJsonProtocol.{StringJsonFormat, immSeqFormat, jsonFormat1}
import spray.json.{JsObject, JsValue, RootJsonFormat, deserializationError}

/** Based on package search's api.v2.ApiMinimalPackage */
final case class ApiPackage(groupId: String, artifactId: String, versions: Seq[String])

object ApiPackage {
  /**
   * `versions` field in package is an array of objects.
   * This case class makes it easier to parse. And possibly extend our model by retrieving more information
   */
  private final case class Version(version: String)
  private implicit val versionJsonFormat: RootJsonFormat[Version] = jsonFormat1(Version.apply)

  private val GroupIdField = "group_id"
  private val ArtifactIdField = "artifact_id"
  private val VersionsField = "versions"

  private val packageFields = List(GroupIdField, ArtifactIdField, VersionsField)

  implicit object ApiPackageJsonFormat extends RootJsonFormat[ApiPackage] {
    override def read(json: JsValue): ApiPackage = json match {
      case JsObject(fields) if packageFields.forall(fields.isDefinedAt) =>
        ApiPackage(
          groupId = fields(GroupIdField).convertTo[String],
          artifactId = fields(ArtifactIdField).convertTo[String],
          versions = fields(VersionsField).convertTo[Seq[Version]].map(_.version)
        )
      case x => deserializationError("JSON deserialization error " + x, fieldNames = packageFields)
    }

    override def write(obj: ApiPackage): JsValue = throw new UnsupportedOperationException
  }
}
