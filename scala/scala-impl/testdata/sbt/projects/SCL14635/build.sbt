name := "SCL-14635"

lazy val root = (project in file("."))
  .dependsOn(
    uriSchemeGit,
    uriSchemeGitWithBranch,
    uriSchemeHttp
  )

// projects with non-file/git uris should import correctly

lazy val uriSchemeGit = RootProject(uri("https://github.com/JetBrains/sbt-idea-plugin.git"))

lazy val uriSchemeGitWithBranch = RootProject(uri("https://github.com/JetBrains/sbt-idea-shell.git#master"))

lazy val uriSchemeHttp = RootProject(uri("https://github.com/JetBrains/sbt-ide-settings.git"))