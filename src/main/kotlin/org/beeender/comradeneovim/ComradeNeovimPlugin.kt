package org.beeender.comradeneovim

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseComponent
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.xmlb.XmlSerializerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.beeender.comradeneovim.core.NvimInstanceManager

val ComradeScope = ComradeNeovimPlugin.instance.coroutineScope

@State(name = "ComradeNeovim",
        storages = [Storage(file = "\$APP_CONFIG\$/comrade_neovim_settings.xml")])
class ComradeNeovimPlugin : BaseComponent, PersistentStateComponent<Settings> {
    companion object {
        val instance: ComradeNeovimPlugin by lazy {
            ApplicationManager.getApplication().getComponent(ComradeNeovimPlugin::class.java)
        }

        val version: String by lazy {
            PluginManager.getPlugin(PluginId.getId("beeender.ComradeNeovim"))!!.version }

        var autoConnect: Boolean
            get() { return instance.settings.autoConnect }
            set(value) {
                if (value) {
                    instance.settings.autoConnect = true
                    NvimInstanceManager.connectAll()
                } else {
                    instance.settings.autoConnect = false
                }
            }
        var showEditorInSync: Boolean
            get() { return instance.settings.showEditorInSync }
            set(value) { instance.settings.showEditorInSync = value }
    }

    private var settings = Settings()
    private lateinit var msgBusConnection: MessageBusConnection
    private lateinit var job: Job

    val coroutineScope by lazy {  CoroutineScope(job + Dispatchers.Default) }

    private val projectManagerListener =  object : ProjectManagerListener {
        override fun projectOpened(project: Project) {
            NvimInstanceManager.refresh()
        }
    }

    override fun initComponent() {
        job = Job()
        NvimInstanceManager.start()
        msgBusConnection = ApplicationManager.getApplication().messageBus.connect()
        msgBusConnection.subscribe(ProjectManager.TOPIC, projectManagerListener)
    }

    override fun disposeComponent() {
        job.cancel()
        NvimInstanceManager.stop()
        msgBusConnection.disconnect()
        super.disposeComponent()
    }

    override fun getState(): Settings {
        return settings
    }

    override fun loadState(state: Settings) {
        XmlSerializerUtil.copyBean(state, settings)
    }
}

