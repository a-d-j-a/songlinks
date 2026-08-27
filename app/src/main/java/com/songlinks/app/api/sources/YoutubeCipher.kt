package com.songlinks.app.api.sources

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

private const val TAG_CIPHER = "YoutubeCipher"

object YoutubeCipher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var cachedPlayerUrl: String? = null
    private var cachedOperations: List<Operation>? = null

    private sealed class Operation {
        object Reverse : Operation()
        data class Splice(val n: Int) : Operation()
        data class Swap(val n: Int) : Operation()
    }

    fun decipherSignature(ciphered: String, videoId: String): String? {
        return try {
            val ops = getOperations(videoId) ?: return null
            var arr = ciphered.toCharArray().toMutableList()
            for (op in ops) {
                when (op) {
                    is Operation.Reverse -> arr.reverse()
                    is Operation.Splice -> {
                        val n = op.n.coerceIn(0, arr.size)
                        arr = arr.subList(n, arr.size).toMutableList()
                    }
                    is Operation.Swap -> {
                        val idx = op.n % arr.size
                        val tmp = arr[0]
                        arr[0] = arr[idx]
                        arr[idx] = tmp
                    }
                }
            }
            arr.joinToString("")
        } catch (e: Exception) {
            Log.e(TAG_CIPHER, "decipher failed", e)
            null
        }
    }

    private fun getOperations(videoId: String): List<Operation>? {
        // Try cache
        cachedOperations?.let { return it }
        val playerUrl = fetchPlayerUrl(videoId) ?: return null
        if (playerUrl == cachedPlayerUrl && cachedOperations != null) return cachedOperations
        val js = fetchJs(playerUrl) ?: return null
        val ops = parseOperations(js) ?: return null
        cachedPlayerUrl = playerUrl
        cachedOperations = ops
        Log.d(TAG_CIPHER, "Parsed ${ops.size} operations from $playerUrl")
        return ops
    }

    private fun fetchPlayerUrl(videoId: String): String? {
        return try {
            // Try embed page which is lighter and less blocked
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
            // Find base.js player url: "/s/player/.../base.js" or "/s/player/.../player_ias.vflset/.../base.js"
            val m = Pattern.compile("\"jsUrl\"\\s*:\\s*\"([^\"]+base\\.js)\"").matcher(body)
            if (m.find()) {
                var url = m.group(1) ?: ""
                url = url.replace("\\/", "/")
                if (url.startsWith("//")) url = "https:$url"
                else if (url.startsWith("/")) url = "https://www.youtube.com$url"
                Log.d(TAG_CIPHER, "Found jsUrl $url")
                return url
            }
            val m2 = Pattern.compile("(/s/player/[^\"']+base\\.js)").matcher(body)
            if (m2.find()) {
                var url = m2.group(1) ?: ""
                if (url.startsWith("/")) url = "https://www.youtube.com$url"
                Log.d(TAG_CIPHER, "Found player $url")
                return url
            }
            Log.w(TAG_CIPHER, "No player url found")
            null
        } catch (e: Exception) {
            Log.e(TAG_CIPHER, "fetchPlayerUrl failed", e)
            null
        }
    }

    private fun fetchJs(playerUrl: String): String? {
        return try {
            val req = Request.Builder()
                .url(playerUrl)
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            resp.close()
            body
        } catch (e: Exception) {
            Log.e(TAG_CIPHER, "fetchJs failed", e)
            null
        }
    }

    private fun parseOperations(js: String): List<Operation>? {
        return try {
            // Find decipher function: often like "a=a.split(\"\");b.X(a,Y);b.Y(a,Z);return a.join(\"\")"
            // First find function that contains a.split("") and a.join("")
            val funcPattern = Pattern.compile("function\\s*\\w*\\s*\\(\\w\\)\\s*\\{[^}]*\\.split\\(\"\"\\)[^}]*\\.join\\(\"\"\\)[^}]*\\}")
            val m = funcPattern.matcher(js)
            var decipherBody: String? = null
            while (m.find()) {
                val body = m.group() ?: ""
                if (body.contains("split") && body.contains("join")) {
                    decipherBody = body
                    // Prefer the one that calls helper object (contains ".")
                    if (body.contains(".")) break
                }
            }
            if (decipherBody == null) {
                // Alternative: look for "a=a.split(\"\");...return a.join"
                val alt = Pattern.compile("a=a\\.split\\(\"\"\\)[^;]*;[^}]*return a\\.join\\(\"\"\\)").matcher(js)
                if (alt.find()) decipherBody = alt.group()
            }
            if (decipherBody == null) {
                Log.w(TAG_CIPHER, "No decipher function found")
                return null
            }
            Log.d(TAG_CIPHER, "Decipher body snippet: ${decipherBody.take(300)}")

            // Find helper object name: look for pattern like "var XX={...}" where object contains functions with splice/split/reverse
            // Extract helper name from decipherBody: e.g., "XX.YZ(a,3)"
            val helperPattern = Pattern.compile("(\\w+)\\.\\w+\\(a,")
            val hm = helperPattern.matcher(decipherBody)
            var helperName: String? = null
            if (hm.find()) helperName = hm.group(1)

            val helperBody: String? = if (helperName != null) {
                // Find "var helperName={...}}" or "let helperName={...}"
                val escaped = Pattern.quote(helperName)
                val p = Pattern.compile("var\\s+$escaped\\s*=\\s*\\{([^}]+\\})").matcher(js)
                // Simpler: find "helperName={"
                val p2 = Pattern.compile("$escaped\\s*:\\s*function[^}]+\\}").matcher(js)
                // Instead, search for object definition
                val objPattern = Pattern.compile("(?:var\\s+)?$escaped\\s*=\\s*\\{([\\s\\S]*?)\\};")
                var hb: String? = null
                val om = objPattern.matcher(js)
                if (om.find()) hb = om.group(1)
                hb
            } else null

            // Map helper functions to operations by inspecting helper object if found
            val funcMap = mutableMapOf<String, String>() // funcName -> operation type
            if (helperName != null && helperBody != null) {
                // helperBody contains "RS:function(a,b){a.splice(0,b)}" etc.
                val funcDefPattern = Pattern.compile("(\\w+)\\s*:\\s*function\\s*\\([^)]*\\)\\s*\\{([^}]+)\\}")
                val fm = funcDefPattern.matcher(helperBody)
                while (fm.find()) {
                    val fname = fm.group(1) ?: continue
                    val fbody = fm.group(2) ?: ""
                    when {
                        fbody.contains("reverse") -> funcMap[fname] = "reverse"
                        fbody.contains("splice") -> funcMap[fname] = "splice"
                        fbody.contains("length") && fbody.contains("%") -> funcMap[fname] = "swap"
                        fbody.contains("a[0]") && fbody.contains("a[") -> funcMap[fname] = "swap"
                        else -> funcMap[fname] = "unknown"
                    }
                }
                // Also handle short form: "YY:function(a){a.reverse()}"
                if (funcMap.isEmpty()) {
                    val shortPattern = Pattern.compile("(\\w+):function\\(\\w\\)\\{\\w\\.reverse\\(\\)\\}")
                    val sm = shortPattern.matcher(helperBody)
                    while (sm.find()) funcMap[sm.group(1) ?: ""] = "reverse"
                }
            }

            // Parse decipherBody calls: helper.func(a, N) or a.reverse() etc.
            val ops = mutableListOf<Operation>()
            // Direct calls like "a.reverse()"
            if (decipherBody.contains(".reverse()")) {
                // Need to find order; simpler parse sequentially
            }

            // Tokenize calls in order
            val callPattern = Pattern.compile("(?:${helperName?.let { Pattern.quote(it) } ?: "\\w+"})\\.(\\w+)\\(a,(\\d+)\\)|a\\.reverse\\(\\)|a\\.splice\\(0,(\\d+)\\)")
            val cm = callPattern.matcher(decipherBody)
            var foundDirect = false
            while (cm.find()) {
                val helperFunc = cm.group(1)
                val numStr = cm.group(2) ?: cm.group(3)
                if (helperFunc != null) {
                    val opType = funcMap[helperFunc] ?: when {
                        helperFunc.contains("reverse", true) -> "reverse"
                        helperFunc.contains("splice", true) -> "splice"
                        else -> "swap"
                    }
                    when (opType) {
                        "reverse" -> ops.add(Operation.Reverse)
                        "splice" -> ops.add(Operation.Splice(numStr?.toIntOrNull() ?: 0))
                        "swap" -> ops.add(Operation.Swap(numStr?.toIntOrNull() ?: 0))
                        else -> {
                            // Try to infer from js helperBody if Available
                            if (numStr != null) ops.add(Operation.Swap(numStr.toIntOrNull() ?: 0))
                            else ops.add(Operation.Reverse)
                        }
                    }
                    foundDirect = true
                } else {
                    // Direct a.reverse() or a.splice
                    val full = cm.group() ?: ""
                    when {
                        full.contains("reverse") -> ops.add(Operation.Reverse)
                        full.contains("splice") -> ops.add(Operation.Splice(numStr?.toIntOrNull() ?: 0))
                    }
                    foundDirect = true
                }
            }

            // Fallback: if no helperName, try to parse simple ops
            if (ops.isEmpty()) {
                // Look for "a.reverse()", "a.splice(0,3)", "var c=a[0];a[0]=a[3%a.length];a[3%a.length]=c"
                if (decipherBody.contains("reverse")) ops.add(Operation.Reverse)
                val splicePat = Pattern.compile("a\\.splice\\(0,(\\d+)\\)")
                val spm = splicePat.matcher(decipherBody)
                while (spm.find()) {
                    ops.add(Operation.Splice(spm.group(1)?.toIntOrNull() ?: 0))
                }
                val swapPat = Pattern.compile("a\\[0\\]=a\\[(\\d+)%a\\.length\\]")
                val swm = swapPat.matcher(decipherBody)
                while (swm.find()) {
                    ops.add(Operation.Swap(swm.group(1)?.toIntOrNull() ?: 0))
                }
            }

            if (ops.isEmpty()) {
                Log.w(TAG_CIPHER, "Parsed 0 operations, decipherBody: $decipherBody")
                return null
            }
            ops
        } catch (e: Exception) {
            Log.e(TAG_CIPHER, "parseOperations failed", e)
            null
        }
    }
}
