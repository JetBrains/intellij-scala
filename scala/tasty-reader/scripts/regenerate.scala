//> using scala 3.10.1
//> using dep org.apache.commons:commons-compress:1.28.0
//> using test.dep org.scalameta::munit:1.3.6

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream

import java.io.IOException
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardCopyOption}
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat
import java.util.zip.{GZIPInputStream, ZipFile}
import scala.jdk.CollectionConverters.*

/** Checks or regenerates the checked-in Scala 3 decompiler fixtures. */
object TastyFixtureRegenerator:
  enum Kind:
    case Compiled, Library

  final case class Group(kind: Kind, path: Path, toolingVersion: String, origin: String, jarSha256: String):
    def directory(root: Path): Path = root.resolve(path)

  private final case class HttpStatusException(status: Int, url: String)
    extends IOException(s"HTTP $status downloading $url")

  private final case class Options(
    regenerate: Boolean,
    dryRun: Boolean,
    allowNew: Boolean,
    compilers: Map[String, Path],
    jars: Map[String, Path],
  )

  private val TastyMagic = HexFormat.of().parseHex("5ca1ab1f")
  private val Usage =
    """Usage: scala-cli run community/scala/tasty-reader/scripts -- --check|--regenerate [options]
      |  --compiler VERSION=PATH  Use a local scalac instead of downloading it
      |  --jar COORDINATE=PATH   Use a local binary JAR instead of downloading it
      |  --dry-run               Prepare regeneration without replacing fixtures
      |  --allow-new             Allow newly compiled TASTy files in the corpus
      |""".stripMargin
  private val CompilerOptions = Map(
    "3.0.0" -> Seq("-Ykind-projector"),
    "3.1.3" -> Seq.empty[String],
    "3.2.2" -> Seq.empty[String],
    "3.3.9" -> Seq.empty[String],
    "3.4.3" -> Seq.empty[String],
    "3.5.2" -> Seq.empty[String],
    "3.6.1" -> Seq("-Xkind-projector"),
    "3.7.4" -> Seq.empty[String],
    "3.8.4" -> Seq.empty[String],
    "3.9.0" -> Seq.empty[String],
    "3.10.0" -> Seq.empty[String],
    "3.10.1" -> Seq.empty[String],
  )
  private val Client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(30))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

  private def fail(message: String): Nothing = throw new IllegalArgumentException(message)

  private def tastyFiles(directory: Path): Set[Path] =
    if !Files.exists(directory) then Set.empty
    else
      val paths = Files.walk(directory)
      try paths.iterator().asScala.filter(path => Files.isRegularFile(path) && path.toString.endsWith(".tasty")).toSet
      finally paths.close()

  private def deleteTree(directory: Path): Unit =
    if Files.exists(directory) then
      val paths = Files.walk(directory)
      try paths.sorted(java.util.Comparator.reverseOrder[Path]()).forEach(path => Files.deleteIfExists(path))
      finally paths.close()

  def groupsFromManifest(root: Path): Seq[Group] =
    val lines = Files.readAllLines(root.resolve("fixture-groups.tsv"), UTF_8).asScala
    val groups = lines.zipWithIndex.collect {
      case (line, index) if line.nonEmpty && !line.startsWith("#") =>
        val columns = line.split("\t", -1).toSeq
        if columns.size != 5 then fail(s"Manifest line ${index + 1} must have five columns")
        val Seq(kindText, pathText, version, origin, checksum) = columns: @unchecked
        val path = Path.of(pathText)
        if path.isAbsolute || path.iterator().asScala.exists(_.toString == "..") then
          fail(s"Invalid fixture path on line ${index + 1}: $pathText")
        val kind = kindText match
          case "compiled" => Kind.Compiled
          case "library" => Kind.Library
          case _ => fail(s"Invalid fixture kind on line ${index + 1}: $kindText")
        if kind == Kind.Compiled && (!origin.startsWith("scala3:") || checksum != "-") then
          fail(s"Invalid compiler origin on line ${index + 1}: $origin")
        if kind == Kind.Library && (origin.split(":", -1).length != 3 || checksum.length != 64) then
          fail(s"Invalid library origin on line ${index + 1}: $origin")
        Group(kind, path, version, origin, checksum)
    }.toSeq
    if groups.isEmpty || groups.map(_.path).distinct.size != groups.size then
      fail("Fixture group paths must be present and unique")
    groups

  def toolingVersion(tastyFile: Path): String =
    val data = Files.readAllBytes(tastyFile)
    if data.length < TastyMagic.length || !data.take(TastyMagic.length).sameElements(TastyMagic) then
      fail(s"Not a TASTy file: $tastyFile")
    var offset = TastyMagic.length

    def readNat(): Int =
      var result = 0
      while offset < data.length do
        val byte = data(offset) & 0xff
        offset += 1
        result = (result << 7) | (byte & 0x7f)
        if (byte & 0x80) != 0 then return result
      fail(s"Truncated TASTy header: $tastyFile")

    (0 until 3).foreach(_ => readNat()) // major, minor, experimental
    val length = readNat()
    if length < 0 || offset.toLong + length > data.length then
      fail(s"Truncated tooling version: $tastyFile")
    new String(data, offset, length, UTF_8)

  def verifyInventory(root: Path, groups: Seq[Group], allowNew: Boolean = false): Unit =
    val all = tastyFiles(root)
    var classified = Set.empty[Path]
    groups.foreach { group =>
      val files = tastyFiles(group.directory(root))
      if files.isEmpty && !(allowNew && group.kind == Kind.Compiled) then
        fail(s"No TASTy fixtures in ${group.path}")
      val overlap = classified.intersect(files)
      if overlap.nonEmpty then fail(s"Fixtures belong to multiple groups: ${overlap.toSeq.sorted.mkString(", ")}")
      files.toSeq.sorted.foreach { fixture =>
        val actual = toolingVersion(fixture)
        if actual != group.toolingVersion then
          fail(s"$fixture: expected ${group.toolingVersion}, found $actual")
      }
      classified ++= files
      println(s"${group.path}: ${files.size} TASTy fixtures (${group.toolingVersion})")
    }
    val unclassified = all -- classified
    if unclassified.nonEmpty then fail(s"Unclassified TASTy fixtures: ${unclassified.toSeq.sorted.mkString(", ")}")

  private def download(url: String, target: Path): Path =
    println(s"Downloading $url")
    val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(120)).GET().build()
    val response = Client.send(request, HttpResponse.BodyHandlers.ofInputStream())
    val body = response.body()
    try
      if response.statusCode() != 200 then throw HttpStatusException(response.statusCode(), url)
      Files.copy(body, target)
    finally body.close()
    target

  private def unpackCompiler(archive: Path, destination: Path): Unit =
    Files.createDirectories(destination)
    val input = new TarArchiveInputStream(new GZIPInputStream(Files.newInputStream(archive)))
    try
      var entry = input.getNextEntry
      while entry != null do
        val target = destination.resolve(entry.getName).normalize()
        if !target.startsWith(destination) || entry.isSymbolicLink || entry.isLink then
          fail(s"Unsafe compiler archive path: ${entry.getName}")
        if entry.isDirectory then Files.createDirectories(target)
        else if entry.isFile then
          Files.createDirectories(target.getParent)
          Files.copy(input, target)
          // Files.copy does not preserve the executable mode from a tar entry.
          if (entry.getMode & 0x49) != 0 && !target.toFile.setExecutable(true) then
            fail(s"Could not make compiler archive entry executable: ${entry.getName}")
        else fail(s"Unsupported compiler archive entry: ${entry.getName}")
        entry = input.getNextEntry
    finally input.close()

  private def compilerFor(version: String, work: Path, overrides: Map[String, Path]): Path =
    val compiler = overrides.getOrElse(version, {
      val archive = download(
        s"https://github.com/scala/scala3/releases/download/$version/scala3-$version.tar.gz",
        work.resolve(s"scala3-$version.tar.gz"),
      )
      val destination = work.resolve(s"scala3-$version")
      unpackCompiler(archive, destination)
      Seq(destination.resolve(s"scala3-$version/bin/scalac"), destination.resolve("bin/scalac"))
        .find(Files.isRegularFile(_)).getOrElse(destination.resolve("bin/scalac"))
    })
    if !Files.isRegularFile(compiler) then fail(s"Scala $version compiler not found: $compiler")
    compiler

  private def run(command: Seq[String], cwd: Path): Unit =
    val process = new ProcessBuilder(command.asJava).directory(cwd.toFile).inheritIO().start()
    val status = process.waitFor()
    if status != 0 then fail(s"Command exited with status $status: ${command.mkString(" ")}")

  private def onPath(command: String): Boolean =
    Option(System.getenv("PATH")).toSeq.flatMap(_.split(java.io.File.pathSeparator))
      .exists(directory => Files.isExecutable(Path.of(directory).resolve(command)))

  private def prepareCompiled(group: Group, output: Path, work: Path, compilers: Map[String, Path], root: Path): Unit =
    val version = group.origin.stripPrefix("scala3:")
    val options = CompilerOptions.getOrElse(version, fail(s"Compiler options are not declared for Scala $version"))
    val sources =
      if !Files.exists(group.directory(root)) then Seq.empty
      else
        val paths = Files.walk(group.directory(root))
        try paths.iterator().asScala.filter(path => Files.isRegularFile(path) && path.toString.endsWith(".scala"))
          .toSeq.sorted
        finally paths.close()
    if sources.isEmpty then fail(s"No Scala sources in ${group.path}")
    sources.foreach { source =>
      val target = output.resolve(group.directory(root).relativize(source))
      Files.createDirectories(target.getParent)
      Files.copy(source, target)
    }
    val relativeSources = sources.map(source => group.directory(root).relativize(source).toString)
    val command = try
      val compiler = compilerFor(version, work, compilers)
      Seq(compiler.toString) ++ options ++ relativeSources
    catch
      case error: HttpStatusException if error.status == 404 && onPath("scala-cli") =>
        println(s"No Scala $version release archive; compiling with scala-cli")
        val cliSources = sources.map { source =>
          val target = work.resolve("scala-cli-source").resolve(version)
            .resolve(group.directory(root).relativize(source))
          Files.createDirectories(target.getParent)
          Files.copy(source, target)
          target.toString
        }
        Seq("scala-cli", "compile") ++ cliSources ++ Seq(
          "--scala", version, "--server=false", "--jvm", "17",
          "--compilation-output", output.toString,
        ) ++ options.flatMap(option => Seq("--scalac-option", option))
    run(command, output)

  private def sha256(path: Path): String =
    val digest = MessageDigest.getInstance("SHA-256")
    val input = Files.newInputStream(path)
    try
      val buffer = new Array[Byte](8192)
      var count = input.read(buffer)
      while count != -1 do
        digest.update(buffer, 0, count)
        count = input.read(buffer)
    finally input.close()
    HexFormat.of().formatHex(digest.digest())

  private def prepareLibrary(group: Group, output: Path, work: Path, jars: Map[String, Path], root: Path): Unit =
    val Array(groupId, artifact, version) = group.origin.split(":", -1): @unchecked
    val jar = jars.getOrElse(group.origin, {
      val url = s"https://repo.maven.apache.org/maven2/${groupId.replace('.', '/')}/$artifact/$version/$artifact-$version.jar"
      download(url, work.resolve(s"$artifact-$version.jar"))
    })
    if sha256(jar) != group.jarSha256 then fail(s"Binary JAR checksum mismatch: $jar")
    val archive = new ZipFile(jar.toFile)
    try
      tastyFiles(group.directory(root)).toSeq.sorted.foreach { fixture =>
        val relative = group.directory(root).relativize(fixture)
        val entryName = relative.toString.replace(java.io.File.separatorChar, '/')
        val entry = archive.getEntry(entryName)
        if entry == null then fail(s"Missing binary JAR entry: $entryName")
        val target = output.resolve(relative)
        Files.createDirectories(target.getParent)
        val input = archive.getInputStream(entry)
        try Files.copy(input, target)
        finally input.close()
      }
    finally archive.close()

  private def verifyGenerated(group: Group, output: Path, root: Path, allowNew: Boolean): Seq[(Path, Path)] =
    val expected = tastyFiles(group.directory(root)).map(group.directory(root).relativize)
    val generated = tastyFiles(output).map(output.relativize)
    val missing = expected -- generated
    val extra = generated -- expected
    if missing.nonEmpty || (extra.nonEmpty && !(allowNew && group.kind == Kind.Compiled)) then
      fail(s"${group.path}: missing ${missing.toSeq.sorted}; unexpected ${extra.toSeq.sorted}")
    generated.toSeq.sorted.map { relative =>
      val file = output.resolve(relative)
      val actual = toolingVersion(file)
      if actual != group.toolingVersion then
        fail(s"$file: expected ${group.toolingVersion}, found $actual")
      (file, group.directory(root).resolve(relative))
    }

  def regenerate(
    root: Path,
    groups: Seq[Group],
    compilers: Map[String, Path],
    jars: Map[String, Path],
    write: Boolean,
    allowNew: Boolean,
  ): Unit =
    val work = Files.createTempDirectory("tasty-fixtures-")
    try
      val replacements = groups.flatMap { group =>
        val output = work.resolve("output").resolve(group.path)
        Files.createDirectories(output)
        group.kind match
          case Kind.Compiled => prepareCompiled(group, output, work, compilers, root)
          case Kind.Library => prepareLibrary(group, output, work, jars, root)
        verifyGenerated(group, output, root, allowNew)
      }
      val changed = replacements.filter { case (generated, current) =>
        !Files.exists(current) || !Files.readAllBytes(generated).sameElements(Files.readAllBytes(current))
      }
      if write then changed.foreach { case (generated, current) =>
        Files.createDirectories(current.getParent)
        val temporary = Files.createTempFile(current.getParent, ".tasty-fixture-", ".tmp")
        try
          Files.copy(generated, temporary, StandardCopyOption.REPLACE_EXISTING)
          Files.move(temporary, current, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        finally Files.deleteIfExists(temporary)
      }
      val action = if write then "Regenerated" else "Checked regeneration of"
      println(s"$action ${replacements.size} fixtures; ${changed.size} differ")
    finally deleteTree(work)

  private def overrideMap(values: Seq[String]): Map[String, Path] =
    values.map { value =>
      val separator = value.indexOf('=')
      if separator < 0 then fail(s"Expected KEY=PATH: $value")
      value.take(separator) -> Path.of(value.drop(separator + 1)).toAbsolutePath.normalize()
    }.toMap

  private def parseOptions(args: Array[String]): Options =
    var check = false
    var regenerate = false
    var dryRun = false
    var allowNew = false
    val compilers = Seq.newBuilder[String]
    val jars = Seq.newBuilder[String]
    var index = 0
    while index < args.length do
      args(index) match
        case "--help" | "-h" =>
          println(Usage)
          scala.sys.exit(0)
        case "--check" => check = true
        case "--regenerate" => regenerate = true
        case "--dry-run" => dryRun = true
        case "--allow-new" => allowNew = true
        case value if value.startsWith("--compiler=") => compilers += value.stripPrefix("--compiler=")
        case value if value.startsWith("--jar=") => jars += value.stripPrefix("--jar=")
        case "--compiler" | "--jar" =>
          val option = args(index)
          index += 1
          if index >= args.length then fail(s"Missing value for $option")
          if option == "--compiler" then compilers += args(index) else jars += args(index)
        case unknown => fail(s"Unknown argument: $unknown")
      index += 1
    if check == regenerate then fail("Choose exactly one of --check or --regenerate")
    if (dryRun || allowNew) && !regenerate then fail("--dry-run and --allow-new require --regenerate")
    Options(regenerate, dryRun, allowNew, overrideMap(compilers.result()), overrideMap(jars.result()))

  def main(args: Array[String]): Unit =
    val options = parseOptions(args)
    val root = Path.of("community/scala/tasty-reader/testdata").toAbsolutePath.normalize()
    val groups = groupsFromManifest(root)
    verifyInventory(root, groups, allowNew = options.allowNew)
    if options.regenerate then
      regenerate(root, groups, options.compilers, options.jars,
        write = !options.dryRun, allowNew = options.allowNew)
