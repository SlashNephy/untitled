package blue.starry.saya.services.nicolive

import blue.starry.saya.models.Comment
import blue.starry.saya.models.JikkyoChannel
import blue.starry.saya.services.LiveCommentProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel

class NicoLiveCommentProvider(
    override val channel: JikkyoChannel,
): LiveCommentProvider {
    override val comments = BroadcastChannel<Comment>(1)
    override val subscription = LiveCommentProvider.Subscription()

    override fun start() = GlobalScope.launch {
        // チャンネル名をタグ名として追加
        val tags = channel.tags.plus(channel.name)

        tags.mapNotNull { tag ->
            val programs = NicoLiveApi.getLivePrograms(tag)
            val search = programs.data.find { data ->
                // コミュニティ放送 or 「ニコニコ実況」タグ付きの公式番組
                !channel.isOfficial || data.tags.any { it.text == "ニコニコ実況" }
            } ?: return@mapNotNull null

            val data = NicoLiveApi.getEmbeddedData("https://live2.nicovideo.jp/watch/${search.id}")

            val ws = NicoLiveSystemWebSocket(this@NicoLiveCommentProvider, data.site.relive.webSocketUrl, search.id)
            ws.start()
        }.joinAll()
    }
}
