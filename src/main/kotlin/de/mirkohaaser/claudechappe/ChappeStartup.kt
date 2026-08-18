package de.mirkohaaser.claudechappe

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/** Wires up the per-project tracker once a project window is up. */
class ChappeStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        ProjectWindowStatus.getInstance(project).start()
    }
}
