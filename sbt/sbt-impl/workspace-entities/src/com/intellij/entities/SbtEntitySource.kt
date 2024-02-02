package com.intellij.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

// note: it is not required to override virtualFileUrl but from what I have found out it is recommended
class SbtEntitySource(override val virtualFileUrl: VirtualFileUrl?) : EntitySource
