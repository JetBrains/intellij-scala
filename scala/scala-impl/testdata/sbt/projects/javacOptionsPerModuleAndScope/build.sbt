name := "javac-options-per-module"
version := "0.1"
scalaVersion := "2.13.4"

javacOptions                     ++= Seq("root_option")
Compile / javacOptions           ++= Seq("root_option_in_compile")
Test / javacOptions              ++= Seq("root_option_in_test")
Compile / compile / javacOptions ++= Seq("root_option_in_compile_compile")

val module1 = project.settings(
  javacOptions ++= Seq("module_1_option"),
  Compile / javacOptions ++= Seq("module_1_option_in_compile"),
  Test / javacOptions ++= Seq("module_1_option_in_test")
)
val module2 = project.settings(
  javacOptions ++= Seq("module_2_option"),
  Compile / javacOptions ++= Seq("module_2_option_in_compile"),
  Test / javacOptions ++= Seq("module_2_option_in_test")
)
val module3 = project.settings(
  Compile / compile / javacOptions ++= Seq("module_3_option_in_compile_compile"),
  Test / javacOptions ++= Seq("module_3_option_in_test")
)