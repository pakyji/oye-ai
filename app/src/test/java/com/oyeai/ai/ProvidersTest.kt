package com.oyeai.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvidersTest {

    @Test
    fun metaPresetPointsAtModelApi() {
        val meta = providerById("meta")!!
        assertEquals("https://api.meta.ai/v1", meta.baseUrl)
        assertEquals("muse-spark-1.3", meta.defaultModel)
    }

    @Test
    fun llamaPresetUsesCompatSurfaceNotNativeV1() {
        val llama = providerById("llama")!!
        // The Llama API's OpenAI-compatible surface is /compat/v1; plain /v1 has no /chat/completions.
        assertEquals("https://api.llama.com/compat/v1/", llama.baseUrl)
        assertEquals("Llama-4-Maverick-17B-128E-Instruct-FP8", llama.defaultModel)
    }

    @Test
    fun noDefaultModelIsKnownDead() {
        // llama-3.3-70b-versatile was shut down for free Groq use on 2026-08-16.
        val dead = setOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant")
        PROVIDERS.forEach { assertTrue("${it.id} default model is dead: ${it.defaultModel}", it.defaultModel !in dead) }
    }

    @Test
    fun allBuiltInProvidersUseHttps() {
        PROVIDERS.forEach { assertTrue(it.id, it.baseUrl.startsWith("https://")) }
    }
}
