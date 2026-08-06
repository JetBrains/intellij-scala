package org.example

interface StartupActivity {
    fun runActivity(project: String)

    interface DumbAwareInner : StartupActivity
}

interface DumbAwareOuter : StartupActivity
