package io.github.deltacv.papervision.engine.previz

import io.github.deltacv.papervision.codegen.CodeGenManager
import io.github.deltacv.papervision.codegen.language.Language
import io.github.deltacv.papervision.codegen.language.jvm.JavaLanguage
import io.github.deltacv.papervision.engine.client.PaperVisionEngineClient
import io.github.deltacv.papervision.engine.client.message.PrevizPingPongMessage
import io.github.deltacv.papervision.engine.client.message.PrevizSourceCodeMessage
import io.github.deltacv.papervision.engine.client.message.PrevizSetStreamResolutionMessage
import io.github.deltacv.papervision.engine.client.response.BooleanResponse
import io.github.deltacv.papervision.engine.client.response.OkResponse
import io.github.deltacv.papervision.io.PipelineStream
import io.github.deltacv.papervision.io.TextureProcessorQueue
import io.github.deltacv.papervision.io.bufferedImageFromResource
import io.github.deltacv.papervision.util.ElapsedTime
import io.github.deltacv.papervision.util.event.EventHandler
import io.github.deltacv.papervision.util.loggerForThis

class ClientPrevizManager(
    val previzStreamWidth: Int,
    val previzStreamHeight: Int,
    val codeGenManager: CodeGenManager,
    val textureProcessorQueue: TextureProcessorQueue,
    val client: PaperVisionEngineClient
) {

    val offlineImages = arrayOf(
        bufferedImageFromResource("/img/TechnicalDifficulties.png"),
        bufferedImageFromResource("/img/PleaseHangOn.png")
    )

    var previzName: String? = null
        private set

    private val pingPongTimer = ElapsedTime()

    var stream = PipelineStream("", client, textureProcessorQueue, offlineImages = offlineImages)
        private set

    val onPrevizStart = EventHandler("ClientPrevizManager-OnPrevizStart")

    val logger by loggerForThis()

    var previzRunning = false
        private set

    fun startPreviz(previzName: String) {
        startPreviz(previzName, JavaLanguage)
    }

    fun startPreviz(previzName: String, language: Language) {
        startPreviz(previzName, codeGenManager.build(previzName, language, true))
    }

    fun startPreviz(previzName: String, sourceCode: String) {
        this.previzName = previzName

        logger.info("Starting previz session $previzName")

        client.sendMessage(PrevizSourceCodeMessage(previzName, sourceCode).onResponse<OkResponse> {
            client.onProcess.doOnce {
                logger.info("Previz session $previzName running")

                previzRunning = true
                pingPongTimer.reset()

                onPrevizStart.run()

                client.sendMessage(PrevizSetStreamResolutionMessage(
                    previzName, previzStreamWidth, previzStreamHeight
                ).onResponse<OkResponse> {
                    client.onProcess.doOnce {
                        stream = PipelineStream(
                            previzName, client, textureProcessorQueue,
                            width = previzStreamWidth, height = previzStreamHeight,
                            offlineImages = offlineImages
                        )
                        stream.start()
                    }
                })
            }
        })
    }

    fun refreshPreviz() = previzName?.let{
        refreshPreviz(codeGenManager.build(it, JavaLanguage, true))
    }

    fun refreshPreviz(sourceCode: String) {
        if(previzRunning)
            client.sendMessage(PrevizSourceCodeMessage(previzName!!, sourceCode))
    }

    fun stopPreviz() {
        logger.info("Stopping previz session $previzName")
        previzRunning = false

        stream.stop()
    }

    fun update() {
        // send every 200 ms
        if(pingPongTimer.seconds > 0.2) {
            if(previzRunning) {
                client.sendMessage(PrevizPingPongMessage(previzName!!).onResponse<BooleanResponse> {
                    previzRunning = it.value
                })
            }

            pingPongTimer.reset()
        }
    }

}