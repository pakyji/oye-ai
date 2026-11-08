package com.oyeai.ai

/**
 * AI providers Oye can use. All of them expose an OpenAI-compatible
 * `POST {baseUrl}/chat/completions` endpoint with a Bearer key.
 * Default models are starting points; each one can be changed in Settings.
 */
data class ProviderSpec(
    val id: String,
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val keyUrl: String
)

val PROVIDERS: List<ProviderSpec> = listOf(
    ProviderSpec("pollinations", "Pollinations", "https://gen.pollinations.ai/v1", "openai", "https://enter.pollinations.ai"),
    ProviderSpec("openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", "https://platform.openai.com/api-keys"),
    ProviderSpec("groq", "Groq", "https://api.groq.com/openai/v1", "openai/gpt-oss-20b", "https://console.groq.com/keys"),
    ProviderSpec("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-3.8-flash", "https://aistudio.google.com/apikey"),
    ProviderSpec("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "openrouter/auto", "https://openrouter.ai/keys"),
    ProviderSpec("mistral", "Mistral", "https://api.mistral.ai/v1", "mistral-small-latest", "https://console.mistral.ai/api-keys"),
    ProviderSpec("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", "https://platform.deepseek.com/api_keys"),
    ProviderSpec("xai", "xAI (Grok)", "https://api.x.ai/v1", "grok-3-mini", "https://console.x.ai"),
    // Meta Model API is OpenAI-compatible at /v1; its key comes from dev.meta.ai.
    ProviderSpec("meta", "Meta", "https://api.meta.ai/v1", "muse-spark-1.3", "https://dev.meta.ai"),
    // The Llama API's OpenAI-compatible surface is /compat/v1, NOT /v1.
    ProviderSpec("llama", "Llama", "https://api.llama.com/compat/v1/", "Llama-4-Maverick-17B-128E-Instruct-FP8", "https://api.llama.com")
)

fun providerById(id: String): ProviderSpec? = PROVIDERS.firstOrNull { it.id == id }
