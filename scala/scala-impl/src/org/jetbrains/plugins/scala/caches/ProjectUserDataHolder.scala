package org.jetbrains.plugins.scala.caches

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Key, UserDataHolder, UserDataHolderEx}
import com.intellij.psi.PsiElement

/**
  * Nikolay.Tropin
  * 28-May-18
  */
trait ProjectUserDataHolder[-E] {
  def dataHolder(e: E): UserDataHolder

  def project(e: E): Project
}

object ProjectUserDataHolder {

  def apply[E: ProjectUserDataHolder]: ProjectUserDataHolder[E] = implicitly

  implicit class Syntax[E: ProjectUserDataHolder](val e: E) {

    def getProject: Project = ProjectUserDataHolder[E].project(e)

    def putUserDataIfAbsent[T](key: Key[T], newValue: T): T = {
      ProjectUserDataHolder[E].dataHolder(e) match {
        case ex: UserDataHolderEx =>
          ex.putUserDataIfAbsent(key, newValue)
        case holder =>
          holder.putUserData(key, newValue)
          newValue
      }
    }

    def getUserData[T](key: Key[T]): T =
      ProjectUserDataHolder[E].dataHolder(e).getUserData(key)
  }

  implicit val psiElement: ProjectUserDataHolder[PsiElement] =
    new ProjectUserDataHolder[PsiElement] {
      override def dataHolder(e: PsiElement): UserDataHolder = e
      override def project(e: PsiElement): Project = e.getProject
    }

  implicit val module: ProjectUserDataHolder[Module] =
    new ProjectUserDataHolder[Module] {
      override def dataHolder(e: Module): UserDataHolder = e
      override def project(e: Module): Project = e.getProject
    }

  implicit val project: ProjectUserDataHolder[Project] =
    new ProjectUserDataHolder[Project] {
      override def dataHolder(e: Project): UserDataHolder = e
      override def project(e: Project): Project = e
    }
}
