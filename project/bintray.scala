import sbt._

object bintrayJetbrains {

  def jbResolver(name: String, patterns: Patterns) =
    Resolver.url(name, url("http://dl.bintray.com/jetbrains/sbt-plugins"))(patterns)

  object Resolvers {
    val mavenPatched  = "jb-maven-patched" at "http://dl.bintray.com/jetbrains/maven-patched/"
    val structureCore = jbResolver("jb-structure-core", Resolver.ivyStylePatterns)
    val structureExtractor012 = jbResolver("jb-structure-extractor-0.12", Patterns.structureExtractor012)
    val structureExtractor013 = jbResolver("jb-structure-extractor-0.13", Patterns.structureExtractor013)
  }

  object Patterns {
    val structureExtractor012 = sbt.Patterns(false, "[organisation]/[module]/scala_2.9.2/sbt_0.12/[revision]/[type]s/[artifact](-[classifier]).[ext]")
    val structureExtractor013 = sbt.Patterns(false, "[organisation]/[module]/scala_2.10/sbt_0.13/[revision]/[type]s/[artifact](-[classifier]).[ext]")
  }

  val allResolvers = Seq(Resolvers.mavenPatched, Resolvers.structureCore,
                         Resolvers.structureExtractor012, Resolvers.structureExtractor013)
}