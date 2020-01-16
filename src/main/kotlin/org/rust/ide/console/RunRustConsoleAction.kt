/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.project.DumbAwareAction
import org.rust.ide.notifications.showBalloonWithoutProject

class RunRustConsoleAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
            ?: return showBalloonWithoutProject("Project not found", NotificationType.ERROR)

        val runner = RsConsoleRunner(project)
        TransactionGuard.submitTransaction(project, Runnable { runner.runSync(true) })
    }
}
