ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.12"

lazy val root = project.in(file("."))
  .settings(name := "ProjectWithModulesWithSameIdsAndNamesWithDifferentCase")

lazy val moduleWithSameIdDifferentCase1 = project.withId("X_my_module_id")
lazy val moduleWithSameIdDifferentCase2 = project.withId("X_My_Module_Id")
lazy val moduleWithSameIdDifferentCase3 = project.withId("X_MY_MODULE_ID")

lazy val moduleWithSameNameDifferentCase1 = project.settings(name := "Y_my_module_name")
lazy val moduleWithSameNameDifferentCase2 = project.settings(name := "Y_My_Module_Name")
lazy val moduleWithSameNameDifferentCase3 = project.settings(name := "Y_MY_MODULE_Name")

lazy val moduleWithSameIdDifferentCaseAndSameNameDifferentCase1 = project.withId("Z_my_module_id").settings(name := "Z_my_module_name")
lazy val moduleWithSameIdDifferentCaseAndSameNameDifferentCase2 = project.withId("Z_My_Module_Id").settings(name := "Z_My_Module_Name")
lazy val moduleWithSameIdDifferentCaseAndSameNameDifferentCase3 = project.withId("Z_MY_MODULE_ID").settings(name := "Z_MY_MODULE_Name")

lazy val moduleWithSameNameIdDifferentCaseAndSameName1 = project.withId("U_my_module_id").settings(name := "same module name")
lazy val moduleWithSameNameIdDifferentCaseAndSameName2 = project.withId("U_My_Module_Id").settings(name := "same module name")
lazy val moduleWithSameNameIdDifferentCaseAndSameName3 = project.withId("U_MY_MODULE_ID").settings(name := "same module name")
