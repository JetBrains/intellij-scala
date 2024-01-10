package com.intellij.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

class SbtEntitySource(override val virtualFileUrl: VirtualFileUrl?) : EntitySource
