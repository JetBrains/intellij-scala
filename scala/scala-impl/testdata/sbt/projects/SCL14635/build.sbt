name := "SCL-14635"

lazy val root = (project in file("."))
  .dependsOn(
    uriSchemeHttp,
    uriSchemeHttpWithTag
  )

// Projects with non-file/git uris should import correctly
//
// NOTE: original ticket (SCL-14635) also included "git://" protocol
// However it's not tested because it's support was dropped.
// If you try using it sbt process might hang OR an error can be produced during the project import:
// >fatal: remote error: The unauthenticated git protocol on port 9418 is no longer supported.
// >Please see https://github.blog/2021-09-01-improving-git-protocol-security-github/ for more information.
// >java.lang.RuntimeException: Nonzero exit code (128): git clone --depth 1 git://github.com/JetBrains/sbt-idea-plugin /home/builduser/.sbt/1.0/staging/519241457c8ee019962d/sbt-idea-plugin
lazy val uriSchemeHttp = RootProject(uri("https://github.com/JetBrains/sbt-ide-settings.git"))
lazy val uriSchemeHttpWithTag = RootProject(uri("https://github.com/JetBrains/sbt-idea-plugin.git#v4.0.3"))