package org.beeender.comradeneovim.completion

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import org.beeender.comradeneovim.buffer.SyncBufferManager
import org.beeender.neovim.annotation.RequestHandler
import org.beeender.neovim.rpc.Request

private val log = Logger.getInstance(SyncBufferManager::class.java)

class CompletionManager(private val bufManager: SyncBufferManager) {
    private class Results(@Volatile var isFinished: Boolean = false) {

        private var candidates = mutableListOf<Map<String, String>>()

        @Synchronized
        fun add(candidate: Candidate) {
            candidates.add(candidate.toMap())
        }

        @Synchronized
        private fun retrieve() : List<Map<String, String>> {
            val ret = candidates.toList()
            candidates.clear()
            return ret
        }

        fun toResponseArgs() : Map<Any, Any> {
            return mapOf( "is_finished" to isFinished,
                    "candidates" to retrieve())
        }

        companion object {
            val EMPTY = Results(true)
        }
    }

    @Volatile
    private var results: Results = Results.EMPTY

    @RequestHandler("comrade_complete")
    fun intellijComplete(req: Request) : Map<Any, Any> {
        val map = req.args.first() as Map<*, *>
        if (map["new_request"] as Boolean) {
            val tmpResults = Results()
            results = tmpResults
            ApplicationManager.getApplication().invokeLater {
                try {
                    doComplete(req, tmpResults)
                }
                catch (t : Throwable) {
                    log.warn("Completion failed.", t)
                }
                finally {
                    tmpResults.isFinished = true
                }
            }
        }

        return results.toResponseArgs()
    }

    private fun doComplete(req: Request, results: Results) {
        val map = req.args.first() as Map<*, *>
        val bufId = map["buf_id"] as Int
        val row = map["row"] as Int
        val col = map["col"] as Int

        val syncedBuf = bufManager.findBufferById(bufId) ?: throw IllegalStateException()
        syncedBuf.moveCaretToPosition(row, col)
        // We need the real editor instead of the delegate here since the completion needs caret.
        val editor = syncedBuf.editor.editor

        val handler = CodeCompletionHandler {
            it.forEach { lookupElement ->
                log.debug("performCompletion: $lookupElement")
                val can = Candidate(lookupElement)
                if (can.valuable) {
                    results.add(can)
                }
            }
        }
        handler.invokeCompletion(syncedBuf.project, editor)
    }
}
