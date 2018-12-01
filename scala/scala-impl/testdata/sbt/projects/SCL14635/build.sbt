name := "SCL-14635"

lazy val root = (project in file("."))
  .dependsOn(
    uriSchemeGit,
    uriSchemeGitWithBranch,
    uriSchemeHttp
  )

// projects with non-file/git uris should import correctly

lazy val uriSchemeGit = RootProject(uri("git://github.com/JetBrains/sbt-idea-plugin"))

lazy val uriSchemeGitWithBranch = RootProject(uri("git://github.com/JetBrains/sbt-idea-shell#master"))

lazy val uriSchemeHttp = RootProject(uri("https://github.com/JetBrains/sbt-ide-settings.git"))