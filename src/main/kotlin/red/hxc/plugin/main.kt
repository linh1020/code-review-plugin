package red.hxc.plugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import red.hxc.plugin.component.ReviewListPanel
import red.hxc.plugin.repository.historyRecords
import red.hxc.plugin.repository.meRecords
import red.hxc.plugin.repository.todayRecords
import red.hxc.plugin.setting.trello
import red.hxc.plugin.setting.trelloName

const val CODE_REVIEW_ID = "CodeReview"
var PROJECT_PATH: String? = null
var globalProject: Project? = null

class CodeReviewComponent(private val project: Project) : Disposable {
    private val toolWindow
        get() = if (project.isDisposed) null else ToolWindowManager.getInstance(project).getToolWindow(CODE_REVIEW_ID)

    override fun dispose() = Unit

    init {
        globalProject = project
        PROJECT_PATH = project.basePath
        showStartupNotification(project)
        initEditorFactoryListener()
        initRepository()
    }

    private fun initRepository() {
        if (dataPersistent.getRepository() == trelloName) {
            val trelloBoardId = dataPersistent.getTrelloBoardId()
            trelloBoardId ?: showNotification(project, CodeReviewBundle.message("c.r.notification.need.config"))
        }
    }


    private fun initEditorFactoryListener() {
        println("initEditorFactoryListener $project.isDisposed")
        if (project.isDisposed) return
        EditorFactory.getInstance().addEditorFactoryListener(
            EditorFactoryListenerImpl(project), this
        )
    }

    fun show(afterShow: (() -> Unit)? = null) = toolWindow?.show {
        afterShow?.invoke()
    }

    fun hide() = toolWindow?.hide()

    fun repaint() {
        toolWindow?.component?.repaint()
    }
}

enum class ContentTab {
    Today, Me, History
}

val contentMap = mutableMapOf<ContentTab, ReviewListPanel?>(
    ContentTab.Today to null,
    ContentTab.Me to null,
    ContentTab.History to null
)

fun refreshReviewContent() {
    trello.refreshAll()
    contentMap[ContentTab.Today]?.reload(todayRecords)
    contentMap[ContentTab.Me]?.reload(mapOf(ContentTab.Me.name to meRecords))
    contentMap[ContentTab.History]?.reload(historyRecords)
}

class CodeReviewToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        trello.refreshAll()
        contentMap[ContentTab.Today] =
            ReviewListPanel(project).apply { reload(todayRecords) }
        contentMap[ContentTab.Me] = ReviewListPanel(project).apply { reload(mapOf(ContentTab.Me.name to meRecords)) }
        contentMap[ContentTab.History] = ReviewListPanel(project).apply { reload(historyRecords) }

        contentMap.forEach { (tabTitle, panel) ->
            toolWindow.contentManager.addContent(
                toolWindow.contentManager.factory.createContent(
                    panel,
                    tabTitle.name,
                    false
                ).apply {
                    isCloseable = false
                }
            )
        }
    }

}

class EditorFactoryListenerImpl(private val project: Project) : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
        try {
            if (event.editor.project == project) {
                project.editorService?.add(event.editor)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        try {
            if (event.editor.project == project) {
                project.editorService?.remove(event.editor)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class EditorService {

    fun add(editor: Editor) {
        val file = editor.document.file as? ReviewDiffVirtualFile
        file?.let {
            println("!it.canCreateMarker || it.side == red.hxc.plugin.ReviewDiffSide.LEFT ${!it.canCreateMarker || it.side == ReviewDiffSide.LEFT}")
            if (!it.canCreateMarker || it.side == ReviewDiffSide.LEFT) return
        }
        GutterIconManager(editor)
    }

    fun remove(editor: Editor) {
    }

    fun addComment(review: Review) {
        trello.createReview(review)
    }
}
