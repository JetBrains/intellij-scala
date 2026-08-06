import com.intellij.util.io.HttpRequests

import scala.collection.mutable

val builder = HttpRequests.request("https://raw.githubusercontent.com/lauris/awesome-scala/master/README.md")
val readmeContent = builder.readString()

val libraryNames = mutable.ArrayBuffer.empty[String]
val companyNames = mutable.ArrayBuffer.empty[String]

val LibraryNameWithUrlRegex = """^\[(.*?)]\((.*?)\).*?$""".r

// NOTE: we handle "most popular libraries" which are highlighted with `**` in the README.md
val Bold = "**"

// Extract list of pairs [libraryName](libraryUrl)
// [**scalatest**](https://github.com/scalatest/scalatest)
readmeContent.linesIterator.foreach {
  case LibraryNameWithUrlRegex(libraryName0, url0) if libraryName0.startsWith(Bold) =>
    val libraryName = libraryName0
      .stripSuffix(Bold)
      .stripPrefix(Bold)
    libraryNames += libraryName

    // url usually contains company name
    val companyName = {
      val url = url0.stripSuffix("/")
      val idxTo = url.lastIndexOf("/")
      val idxFrom = url.lastIndexOf("/", idxTo - 1)
      url.substring(idxFrom + 1, idxTo)
    }

    companyNames += companyName
  case _ =>
}

libraryNames.foreach(println)
companyNames.foreach(println)

val TokenSplitRegex = """[-_.\d]"""
def tokenize(string: String): Iterable[String] = string.split(TokenSplitRegex)

def buildDict(words: Iterable[String]): Set[String] = {
  val tokens0 = words.flatMap(tokenize).toSet
  val tokens = tokens0.filter(_.length > 3) // spellchecker doesn't check short works anyway
  val tokensLowercased = tokens.map(_.toLowerCase)
  tokensLowercased
}

val dictionary1 = buildDict(libraryNames)
val dictionary2 = buildDict(companyNames) - dictionary1

//noinspection SpellCheckingInspection
// the list was manually build after analyzing wired libraryNames parts
// which actually LOOK LIKE a typo
val ignoreTokens = Set(
  //lib names
  "blindsight",
  "cassovary",
  "dregex",
  "enableif",
  "fansi",
  "freasy",
  "groll",
  "jawn",
  "jefe",
  "kantan",
  "lamma",
  "lomrf",
  "pprint",
  "salat",
  "spata",
  "stac",
  "sttp",
  "tsec",
  "youi",
  //org names
  "acinq",
  "agourlay",
  "biddata",
  "debasishg",
  "etaty",
  "jaliss",
  "ktoso",
  "mohiva",
  "nulab",
  "poslavskysv",
  "splink",
  "stups",
  "wvlet",
  "xerial",
)

val `awesome-scala-lib-name-parts.dic` = buildDict(libraryNames) -- ignoreTokens
val `awesome-scala-org-name-parts.dic` = buildDict(companyNames) -- `awesome-scala-lib-name-parts.dic` -- ignoreTokens

// 1. pass the outputs to
// org.jetbrains.plugins.scala.spellchecker.FilterValidWorksWithSpellChecker
// 2. copy the result to corresponding `*.dic` files
`awesome-scala-lib-name-parts.dic`.toSeq.sortBy(_.toLowerCase).foreach(println)

//NOTE: actually this is not "org" name from the maven repository
// for some libraries it can be equal, for some - not
`awesome-scala-org-name-parts.dic`.toSeq.sortBy(_.toLowerCase).foreach(println)
