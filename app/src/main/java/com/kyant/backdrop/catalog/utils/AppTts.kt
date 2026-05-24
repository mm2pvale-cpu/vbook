package com.kyant.backdrop.catalog.utils

import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class AppTts {
    private var currentPlayer: MediaPlayer? = null
    private var nextPlayer: MediaPlayer? = null
    var isPlaying = false
    private var playlist = mutableListOf<String>()
    var onComplete: (() -> Unit)? = null

    suspend fun speak(text: String, isItalian: Boolean) {
        stop()
        isPlaying = true
        
        // Amazon Polly Neural Voices via StreamElements API (Free, Instant)
        val voice = if (isItalian) "Bianca" else "Joanna"
        
        val maxChunk = 300
        val words = text.split(" ")
        var currentChunk = ""
        val chunks = mutableListOf<String>()
        
        for (word in words) {
            if (currentChunk.length + word.length > maxChunk) {
                chunks.add(currentChunk.trim())
                currentChunk = word + " "
            } else {
                currentChunk += word + " "
            }
        }
        if (currentChunk.isNotBlank()) chunks.add(currentChunk.trim())
        
        if (chunks.isEmpty()) {
            isPlaying = false
            return
        }
        
        playlist.addAll(chunks.map { 
            "https://api.streamelements.com/kappa/v2/speech?voice=$voice&text=${URLEncoder.encode(it, "UTF-8")}" 
        })
        
        playNext()
    }

    private fun playNext() {
        if (playlist.isEmpty()) {
            isPlaying = false
            onComplete?.invoke()
            return
        }
        
        try {
            val url = playlist.removeAt(0)
            val player = MediaPlayer().apply {
                setDataSource(url)
                prepareAsync()
                setOnPreparedListener { 
                    it.start() 
                    prepareNextPlayer()
                }
                setOnCompletionListener { 
                    it.release()
                    currentPlayer = nextPlayer
                    nextPlayer = null
                    if (currentPlayer != null) {
                        currentPlayer?.start()
                        prepareNextPlayer()
                    } else {
                        playNext()
                    }
                }
                setOnErrorListener { _, _, _ ->
                    playNext()
                    true
                }
            }
            currentPlayer = player
        } catch (e: Exception) {
            playNext()
        }
    }
    
    private fun prepareNextPlayer() {
        if (playlist.isEmpty()) return
        try {
            val url = playlist.removeAt(0)
            nextPlayer = MediaPlayer().apply {
                setDataSource(url)
                prepareAsync()
            }
            currentPlayer?.setNextMediaPlayer(nextPlayer)
        } catch (e: Exception) {
            // Ignore format errors for next chunk
        }
    }

    fun stop() {
        isPlaying = false
        playlist.clear()
        try {
            currentPlayer?.stop()
            currentPlayer?.release()
        } catch (e: Exception) {}
        try {
            nextPlayer?.stop()
            nextPlayer?.release()
        } catch (e: Exception) {}
        currentPlayer = null
        nextPlayer = null
    }
}
