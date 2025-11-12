package org.jetbrains.plugins.scala.externalLibraries.scio

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingTestLike
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.util.dependencymanager.TestDependencyManagers
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

/**
 * Tests for ScioInjector: ensure that classes annotated with
 * AvroType macros do not produce errors in the editor and that
 * synthetic supertype injection does not break resolve.
 *
 * The examples are adapted from SCL-24603 comments. Instead of checking
 * only around the caret, we assert that the whole file has no errors.
 */
class ScioInjectorTest extends ScalaLightCodeInsightFixtureTestCase with ScalaHighlightingTestLike {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_2_13

  // No external libraries: we provide minimal stubs for AvroType and HasAvroAnnotation in test code
  override def additionalLibraries: Seq[LibraryLoader] = Seq(
    //ATTENTION: this test downloads transitive dependencies that can take quite long
    IvyManagedLoader(TestDependencyManagers.IgnoringScalaLibrary, ("com.spotify" %% "scio-avro" % "0.14.20").transitive())
  )

  def testRecogniseToSchemaAnnotation(): Unit = assertNoErrors(
    s"""import com.spotify.scio.values.SCollection
       |import com.spotify.scio.avro._
       |import com.spotify.scio.avro.types.AvroType
       |
       |// injects: com.spotify.scio.avro.types.AvroType.HasAvroAnnotation in the class hierarchy
       |@AvroType.toSchema
       |case class Foo(x: Int, s: String)
       |
       |object Usage {
       |  val seq: SCollection[Foo] = ???
       |
       |  def result = seq.saveAsTypedAvroFile("gs://path-to-data/lake/output")
       |}
       |""".stripMargin
  )

  def testRecogniseToSchemaAndFromSchemaAnnotations(): Unit = assertNoErrors(
    s"""import com.spotify.scio.ScioContext
       |import com.spotify.scio.avro._
       |import com.spotify.scio.avro.types.AvroType
       |import com.spotify.scio.values.SCollection
       |import org.apache.avro.Schema
       |import org.apache.avro.specific.SpecificRecord
       |
       |class Account extends SpecificRecord {
       |  override def put(i: Int, v: Any): Unit = ???
       |  override def get(i: Int): AnyRef = ???
       |  override def getSchema: Schema = ???
       |}
       |@AvroType.toSchema
       |case class AccountToSchema(id: Int, `type`: String, name: String, amount: Double)
       |
       |@AvroType.fromSchema(
       |  \"\"\"{
       | "type":"record",
       | "name":"Account",
       | "namespace":"com.spotify.scio.avro",
       | "doc":"Record for an account",
       | "fields":[
       |   {"name":"id","type":"int"},
       |   {"name":"type","type":"string"},
       |   {"name":"name","type":"string"},
       |   {"name":"amount","type":"double"}
       | ]
       |}
       |\"\"\")
       |class AccountFromSchema
       |
       |
       |//commented out because compilation will stuck if the file does not exist
       |//@AvroType.fromSchemaFile("./todo.txt")
       |//class AccountFromSchemaFile
       |
       |object Usage2 {
       |  val seq: SCollection[AccountToSchema] = ???
       |  seq.saveAsTypedAvroFile("test")
       |
       |  val sc: ScioContext = ???
       |  sc.typedAvroFile[AccountFromSchema]("test")
       |  //sc.typedAvroFile[AccountFromSchemaFile]("test")
       |}
       |""".stripMargin
  )
}
