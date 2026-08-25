import re

with open("app/src/main/java/com/example/ui/ExoVideoPlayer.kt", "r") as f:
    content = f.read()

target_block = """                override fun onPlayerError(error: PlaybackException) {
                    Log.w("ExoVideoPlayer", "Player error: [${error.errorCodeName}] ${error.message}")
                    hasPlaybackError = true
                    playbackErrorMessage = when (error.errorCode) {
                        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Server video menolak koneksi (HTTP 403/404)"
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Koneksi jaringan internet terputus atau timeout"
                        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "Format HLS manifest tidak valid"
                        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                        PlaybackException.ERROR_CODE_DECODING_FAILED -> "Codec video perangkat tidak mendukung stream ini"
                        else -> "Gagal memutar video: ${error.errorCodeName}"
                    }
                }"""

replacement_block = """                override fun onPlayerError(error: PlaybackException) {
                    Log.w("ExoVideoPlayer", "Player error: [${error.errorCodeName}] ${error.message}")
                    
                    // Auto-fallback for container/manifest errors or 403s!
                    if (error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                        error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
                        Log.i("ExoVideoPlayer", "Auto-switching to Web Player due to protected/malformed stream")
                        useNativeExo = false
                        return
                    }

                    hasPlaybackError = true
                    playbackErrorMessage = when (error.errorCode) {
                        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Server video menolak koneksi (HTTP 403/404)"
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Koneksi jaringan internet terputus atau timeout"
                        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "Format HLS manifest tidak valid"
                        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                        PlaybackException.ERROR_CODE_DECODING_FAILED -> "Codec video perangkat tidak mendukung stream ini"
                        else -> "Gagal memutar video: ${error.errorCodeName}"
                    }
                }"""

new_content = content.replace(target_block, replacement_block)

if target_block in content:
    with open("app/src/main/java/com/example/ui/ExoVideoPlayer.kt", "w") as f:
        f.write(new_content)
    print("Success")
else:
    print("Target block not found. Could not replace.")

