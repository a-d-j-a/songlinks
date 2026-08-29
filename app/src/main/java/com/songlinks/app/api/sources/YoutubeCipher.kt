package com.songlinks.app.api.sources

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val TAG_CIPHER = "YoutubeCipher"

object YoutubeCipher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var cachedPlayerUrl: String? = null
    private var cachedOps: List<YtOperation>? = null

    sealed class YtOperation {
        object Reverse : YtOperation()
        data class Splice(val n: Int) : YtOperation()
        data class Swap(val n: Int) : YtOperation()
    }

    fun decipherSignature(ciphered: String, videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            // Step 1: Try cached operations first
            cachedOps?.let { if (it.isNotEmpty()) return@withContext ciphered.decipherOps(it) }

            // Step 2: Fetch player page and extract operations
            val playerUrl = fetchPlayerUrl(videoId) ?: return null
            if (playerUrl != cachedPlayerUrl) {
                cachedPlayerUrl = playerUrl
                val js = fetchJs(playerUrl) ?: return null
                cachedOps = parseOperations(js) ?: return null
            }
            ciphered.decipherOps(cachedOps!!)
        } catch (e: Exception) {
            Log.e(TAG_CIPHER, "decipherSignature failed for $videoId", e)
            null
        }
    }

    private data class YtOp(val typ: String, val n: Int?)

    private fun String.decipherOps(ops: List<YtOp>): String {
        var arr = this.toCharArray().toMutableList()
        for (op in ops) {
            when (op.typ) {
                "reverse" -> arr.reverse()
                "splice" -> {
                    val n = op.n.coerceIn(0, arr.size)
                    arr = arr.subList(n, arr.size).toMutableList()
                }
                "swap" -> {
                    val n = op.n % arr.size
                    val tmp = arr[0]
                    arr[0] = arr[n]
                    arr[n] = tmp
                }
            }
        }
        arr.joinToString("")
    }

    private fun fetchPlayerUrl(videoId: String): String? = try {
        // Try embed page (lighter, less blocked)
        val watchUrl = "https://www.youtube.com/watch?v=$videoId&hl=en&gl=US&has_verified=1&bpctr=9999999999"
        val req = Request.Builder()
            .url(watchUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36")
            .header("Cookie", "CONSENT=YES+1; SOCS=CAI")
            .header("Accept-Language", "en-US,en;q=0.9")
            .get()
            .build()
        val resp = client.newCall(req).execute()
        val body = resp.body?.string() ?: ""
        resp.close()

        // Find base.js URL: may be in "jsUrl" or a script src path
        val m1 = """["'](/s/player[^"']*base\.js)["']""".r().matcher(body)
        if (m1.find()) {
            val url = "https://www.youtube.com${m1.group(1)}"
            Log.d(TAG_CIPHER, "fetchPlayerUrl base.js: $url")
            return url
        }

        // Alternative: look for "playerUrl" or "baseUrl" in the initial data
        val m2 = """["']baseJs["']:\s*["']([^"']+)["']""".r().matcher(body)
        if (m2.find()) {
            val url = m2.group(1)
            Log.d(TAG_CIPHER, "fetchPlayerUrl baseJs: $url")
            return url
        }

        Log.w(TAG_CIPHER, "No player URL found in watch page")
        null
    } catch (e: Exception) {
        Log.e(TAG_CIPHER, "fetchPlayerUrl exception", e)
        null
    }

    private fun fetchJs(playerUrl: String): String? = try {
        val req = Request.Builder()
            .url(playerUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko)")
            .header("Accept", "*/*")
            .get()
            .build()
        val resp = client.newCall(req).execute()
        val body = resp.body?.string() ?: ""
        resp.close()
        Log.d(TAG_CIPHER, "fetchedJs ${body.substring(0, minOf(200, body.length))}...")
        body
    } catch (e: Exception) {
        Log.e(TAG_CIPHER, "fetchJs exception", e)
        null
    }

    /** Parse YouTube's obfuscated player JS to extract reverse/splice/swap operations. */
    private fun parseOperations(js: String): List<YtOp>? = try {
        val ops = mutableListOf<YtOp>()

        // --- Helper detection: find the obfuscation helper object ---
        // Pattern: "var $NAME = { ... }" or "var $NAME = function() { ... }"
        var helperName: String? = null

        // Try "var cipher={...}" or "var b={...}" patterns
        val helperPatterns = listOf(
            Pattern.compile("var\\s+(\\w+)\\s*="),
            Pattern.compile("let\\s+(\\w+)\\s*="),
            Pattern.compile("const\\s+(\\w+)\\s*=")
        )

        for (pattern in helperPatterns) {
            val m = pattern.matcher(js)
            if (m.find()) {
                val candidate = m.group(1)
                // Verify this helper contains splice/reverse/swap methods
                if (js.substring(m.start()).contains("splice") || js.substring(m.start()).contains("reverse") || js.substring(m.start()).contains("swap")) {
                    helperName = candidate
                    Log.d(TAG_CIPHER, "Helper detected: $helperName")
                    break
                }
            }
        }

        // --- Extract helper methods (splice, reverse, swap) ---
        if (helperName != null) {
            // Find the helper object definition: "var NAME={...}" or "NAME={...}"
            val escaped = java.util.regex.Pattern.quote(helperName)
            val objPattern = Pattern.compile("(?:var\\s+)?$escaped\\s*=\\s*\\{([\\s\\S]*?)\\};")
            val om = objPattern.matcher(js)
            var helperBody: String? = null
            if (om.find()) helperBody = om.group(1)

            if (helperBody != null) {
                // Parse function definitions inside helper: "funcName:function(params){body}"
                val funcDefPattern = Pattern.compile("(\\w+)\\s*:\\s*function\\s*\\([^)]*\\)\\s*\\{([^}]+)\\}")
                val fm = funcDefPattern.matcher(helperBody)
                val funcMap = mutableMapOf<String, String>() // funcName -> "reverse"|"splice"|"swap"

                while (fm.find()) {
                    val fname = fm.group(1)
                    val fbody = fm.group(2)
                    when {
                        fbody.contains("reverse") && !fbody.contains("splice") -> funcMap[fname] = "reverse"
                        fbody.contains("splice") -> funcMap[fname] = "splice"
                        fbody.contains("swap") || (fbody.contains("a[0]") && fbody.contains("a[")) -> funcMap[fname] = "swap"
                        else -> /* skip */
                    }
                }

                // Also check short form: "funcName:function(a){a.reverse()}"
                if (funcMap.isEmpty()) {
                    val shortPattern = Pattern.compile("(\\w+):function\\(\\w\\)\\{\\w\\.reverse\\(\\)\\}")
                    val sm = shortPattern.matcher(helperBody)
                    while (sm.find()) funcMap[sm.group(1) ?: ""] = "reverse"
                }

                // Tokenize the decipher calls: helper.func(a, N) or a.reverse() etc.
                if (funcMap.isNotEmpty()) {
                    // Calls like: helperName.reverse(a), helperName.splice(0, N), helperName.swap(N)
                    val callPattern = Pattern.compile("${helperName}\\.(\\w+)\\(a,(\\d+)\\)|${helperName}\\.(\\w+)\\(0,(\\d+)\\)|${helperName}\\.(\\w+)\\(\\)")
                    val cm = callPattern.matcher(helperBody)
                    while (cm.find()) {
                        val method = cm.group(1) ?: cm.group(3) ?: cm.group(5) ?: ""
                        val numStr = cm.group(2) ?: cm.group(4) ?: cm.group(6) ?: null
                        val opType = funcMap[method] ?: when {
                            method.contains("reverse") -> "reverse"
                            method.contains("splice") -> "splice"
                            method.contains("swap") -> "swap"
                            else -> null
                        }
                        when (opType) {
                            "reverse" -> ops.add(YtOp.Reverse, null)
                            "splice" -> ops.add(YtOp.Splice(numStr?.toIntOrNull() ?: 0))
                            "swap" -> ops.add(YtOp.Swap(numStr?.toIntOrNull() ?: 0))
                        }
                    }
                }
            }
        }

        // --- Fallback: parse direct calls in the full JS if helper not found ---
        if (ops.isEmpty()) {
            // Look for "a.reverse()", "a.splice(0, N)", "a.swap(N)" patterns
            if (js.contains("a.reverse") || js.contains(".reverse()")) ops.add(YtOp.Reverse, 0)

            val splicePat = Pattern.compile("a\\.splice\\(0,(\\d+)\\)")
            val spm = splicePat.matcher(js)
            while (spm.find()) ops.add(YtOp.Splice(spm.group(1)?.toIntOrNull() ?: 0))

            val swapPat = Pattern.compile("a\\[0\\]=a\\[(\\d+)%a\\.length\\]")
            val swm = swapPat.matcher(js)
            while (swm.find()) ops.add(YtOp.Swap(swm.group(1)?.toIntOrNull() ?: 0))

            // Also look for "var c=a[0];a[0]=a[N%a.length];a[N%a.length]=c" style swaps
            val swapPat2 = Pattern.compile("a\\[(\\d+)%a\\.length\\]=[aA]\\[0\\]")
            val swm2 = swapPat2.matcher(js)
            while (swm2.find()) ops.add(YtOp.Swap(swm2.group(1)?.toIntOrNull() ?: 0))
        }

        // Final: if we still have 0 ops, try very permissive matching
        if (ops.isEmpty()) {
            // Very permissive: grab any "func(a, N)" pattern that looks like an op
            val permissive = Pattern.compile("\\w+\\.\\(a,(\\d+)\\)|\\w+\\(\\)|a\\.reverse\\(|\\[a\\].reverse")
            val pm = permissive.matcher(js)
            while (pm.find()) {
                val m = pm.group()
                when {
                    m.contains("reverse") -> ops.add(YtOp.Reverse, 0)
                    m.contains("splice") -> ops.add(YtOp.Splice(0))
                    else -> ops.add(YtOp.Swap(0))
                }
            }
        }

        if (ops.isEmpty()) {
            Log.w(TAG_CIPHER, "parseOperations: 0 ops extracted from JS snippet")
            return null
        }
        Log.d(TAG_CIPHER, "parseOperations extracted ${ops.size} ops")
        ops
    } catch (e: Exception) {
        Log.e(TAG_CIPHER, "parseOperations failed", e)
        null
    }
}