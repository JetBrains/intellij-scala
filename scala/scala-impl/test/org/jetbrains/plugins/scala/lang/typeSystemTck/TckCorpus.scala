package org.jetbrains.plugins.scala.lang.typeSystemTck

import com.google.gson.{JsonObject, JsonParser}

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters._

/**
 * Loader for the scala-type-system-tck corpus (see ~/code/scala-type-system-tck).
 *
 * For now the corpus is referenced directly from a sibling checkout; later it may
 * be consumed as a build dependency and unpacked. Override the location with the
 * `SCALA_TCK_CORPUS` env var or `-Dscala.tck.corpus=...` system property.
 *
 * The data model mirrors the TCK's `tck.json` / `expected.json`. We parse with
 * Gson (bundled with the platform) rather than introduce a Scala JSON dep.
 */
object TckCorpus {

  final case class TypeDecl(name: String, expr: String, anchor: Option[String])
  final case class ConformanceQuery(lhs: String, rhs: String, expect: Boolean)

  final case class Entry(
    id: String,
    description: String,
    source: String,
    types: Seq[TypeDecl],
    conformance: Seq[ConformanceQuery],
    baseTypeSeqQueries: Seq[String],
    // golden: query name -> ordered rendered base types (from the scalac oracle)
    goldenBaseTypeSeq: Map[String, Seq[String]],
    // golden: query name -> ordered linearization class names (baseClasses)
    goldenBaseClasses: Map[String, Seq[String]],
    // term probes (`val __t_<name> = <expr>`) and their golden inferred types
    termTypeQueries: Seq[TypeDecl],
    goldenTermTypes: Map[String, String]
  )

  def root(): Path = {
    val configured =
      Option(System.getProperty("scala.tck.corpus"))
        .orElse(Option(System.getenv("SCALA_TCK_CORPUS")))
    val base = configured.map(Paths.get(_)).getOrElse {
      Paths.get(System.getProperty("user.home"), "code", "scala-type-system-tck", "corpus")
    }
    require(Files.isDirectory(base), s"TCK corpus not found at $base (set -Dscala.tck.corpus=...)")
    base
  }

  def load(): Seq[Entry] = {
    val corpus = root()
    Files.list(corpus).iterator().asScala.toSeq
      .filter(Files.isDirectory(_))
      .sortBy(_.getFileName.toString)
      .map(loadEntry)
  }

  private def loadEntry(dir: Path): Entry = {
    val id = dir.getFileName.toString
    val tck = parseObject(dir.resolve("tck.json"))
    val source = Files.readString(dir.resolve("source.scala"))

    val types = tck.getAsJsonArray("types").asScala.toSeq.map { e =>
      val o = e.getAsJsonObject
      TypeDecl(
        name = o.get("name").getAsString,
        expr = o.get("expr").getAsString,
        anchor = if (o.has("anchor")) Some(o.get("anchor").getAsString) else None
      )
    }
    val conformance = tck.getAsJsonArray("conformance").asScala.toSeq.map { e =>
      val o = e.getAsJsonObject
      ConformanceQuery(o.get("lhs").getAsString, o.get("rhs").getAsString, o.get("expect").getAsBoolean)
    }
    val btsQueries = tck.getAsJsonArray("baseTypeSeq").asScala.toSeq.map(_.getAsString)
    val termTypes =
      if (tck.has("termTypes")) tck.getAsJsonArray("termTypes").asScala.toSeq.map { e =>
        val o = e.getAsJsonObject
        TypeDecl(o.get("name").getAsString, o.get("expr").getAsString,
          if (o.has("anchor")) Some(o.get("anchor").getAsString) else None)
      } else Seq.empty

    val goldenObj = if (Files.exists(dir.resolve("expected.json"))) Some(parseObject(dir.resolve("expected.json"))) else None
    def goldenMap(field: String): Map[String, Seq[String]] =
      goldenObj.filter(_.has(field)).map { o =>
        o.getAsJsonObject(field).entrySet().asScala.map { en =>
          en.getKey -> en.getValue.getAsJsonArray.asScala.toSeq.map(_.getAsString)
        }.toMap
      }.getOrElse(Map.empty)
    def goldenStringMap(field: String): Map[String, String] =
      goldenObj.filter(_.has(field)).map { o =>
        o.getAsJsonObject(field).entrySet().asScala.map(en => en.getKey -> en.getValue.getAsString).toMap
      }.getOrElse(Map.empty)

    Entry(id, descriptionOf(tck), source, types, conformance, btsQueries,
      goldenMap("baseTypeSeq"), goldenMap("baseClasses"), termTypes, goldenStringMap("termTypes"))
  }

  private def descriptionOf(tck: JsonObject): String =
    if (tck.has("description")) tck.get("description").getAsString else ""

  private def parseObject(p: Path): JsonObject =
    JsonParser.parseString(Files.readString(p)).getAsJsonObject

  // --- synthetic compilation unit, mirroring the scalac reference engine ---

  val WrapperPkg = "__tck"
  val WrapperObj = "Corpus"
  val QueryPrefix = "__q_"
  val TermPrefix = "__t_"

  private def alias(d: TypeDecl): String = s"type $QueryPrefix${d.name} = ${d.expr}"
  private def probe(d: TypeDecl): String = s"val $TermPrefix${d.name} = ${d.expr}"

  /**
   * Wrap the preamble plus one `type __q_<name> = <expr>` alias per type query and
   * one `val __t_<name> = <expr>` per term probe into a single object. Anchored
   * decls are spliced at their `/*ANCHOR id*/` marker so they resolve in that
   * template's context (this.type, self-type, this.Member); the rest become
   * top-level members of the corpus object.
   */
  def wrap(entry: Entry): String = {
    val decls: Seq[(Option[String], String)] =
      entry.types.map(d => d.anchor -> alias(d)) ++
        entry.termTypeQueries.map(d => d.anchor -> probe(d))
    val (anchored, topLevel) = decls.partition(_._1.isDefined)

    var body = entry.source
    anchored.groupBy(_._1.get).foreach { case (id, ds) =>
      val marker = s"/*ANCHOR $id*/"
      require(body.contains(marker), s"[${entry.id}] anchor '$id' not found in source.scala")
      body = body.replace(marker, marker + " ; " + ds.map(_._2).mkString(" ; "))
    }

    val topDecls = topLevel.map("  " + _._2).mkString("\n")
    s"""package $WrapperPkg
       |object $WrapperObj {
       |${body.linesIterator.map("  " + _).mkString("\n")}
       |$topDecls
       |}
       |""".stripMargin
  }
}
