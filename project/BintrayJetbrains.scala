import sbt._

object BintrayJetbrains {
  import Resolvers._

  def jbBintrayResolverIvy(name: String, repo: String, patterns: Patterns): URLRepository =
    Resolver.url(name, url(s"https://dl.bintray.com/jetbrains/$repo"))(patterns)

  def jbSbtResolver(name: String, patterns: Patterns): URLRepository =
    jbBintrayResolverIvy(name, "sbt-plugins", patterns)

  object Resolvers {
    val mavenPatched  = "jb-maven-patched" at "https://dl.bintray.com/jetbrains/maven-patched/"
    val scalaTestFindersPatched  = jbBintrayResolverIvy("scalatest-finders-patched", "scalatest", Resolver.ivyStylePatterns)
    val scalaPluginDeps  = jbBintrayResolverIvy("scala-plugin-deps", "scala-plugin-deps", Resolver.ivyStylePatterns)
    val scalaPluginDepsMvn  = Resolver.bintrayRepo("jetbrains","scala-plugin-deps")
    val sonatypeReleases = Resolver.sonatypeRepo("releases")
    val sonatypeStaging = Resolver.sonatypeRepo("staging")
    val metaBintray = Resolver.url("scalameta-bintray", url("https://dl.bintray.com/scalameta/maven"))(Resolver.ivyStylePatterns)
    val macrosMaven = Resolver.bintrayRepo("scalamacros", "maven")
    val jbSbtPlugins = jbSbtResolver("jetbrains-sbt", Resolver.ivyStylePatterns)
    val scalaCenter = Resolver.bintrayRepo("scalacenter","releases")
  }

  val allResolvers = Seq(mavenPatched, metaBintray, jbSbtPlugins, scalaTestFindersPatched, scalaPluginDeps,
    scalaPluginDepsMvn, sonatypeReleases, macrosMaven, scalaCenter, sonatypeStaging)
}
