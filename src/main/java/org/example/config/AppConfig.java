package org.example.config;

public class AppConfig {

    // ── Provider selection ───────────────────────────────────────────────────
    // Change PROVIDER to switch between LLM backends.
    // Override at runtime with -Dllm.provider=GEMINI (or ANTHROPIC / OPENROUTER / OLLAMA)
    public enum Provider { ANTHROPIC, OPENROUTER, GEMINI, OLLAMA, NVIDIA }

    public static final Provider PROVIDER = Provider.valueOf(
            System.getProperty("llm.provider", "NVIDIA").toUpperCase()
    );

    // ── Model IDs per provider ───────────────────────────────────────────────
    public static final String ANTHROPIC_MODEL  = "claude-sonnet-4-6";
    //public static final String OPENROUTER_MODEL = "meta-llama/llama-3.3-70b-instruct";
   // public static final String OPENROUTER_MODEL = "google/gemini-2.5-flash";
    public static final String OPENROUTER_MODEL = "meta-llama/llama-4-maverick";

    public static final String GEMINI_MODEL          = "gemini-3.5-flash";
    public static final String OLLAMA_MODEL          = "llama3.2";   // must be pulled locally
    // NVIDIA NIM models — switch NVIDIA_MODEL to select between them
    public static final String NVIDIA_DEEPSEEK_MODEL = "deepseek-ai/deepseek-v4-pro";
    public static final String NVIDIA_KIMI_MODEL     = "moonshotai/kimi-k2-instruct";
    public static final String NVIDIA_DEEPSEEK_FLASH_MODEL = "deepseek-ai/deepseek-v4-flash";
    public static final String NVIDIA_MODEL          = NVIDIA_DEEPSEEK_FLASH_MODEL;

    // ── API keys from environment ────────────────────────────────────────────
    public static final String ANTHROPIC_API_KEY  = System.getenv("ANTHROPIC_API_KEY");
    public static final String OPENROUTER_API_KEY = System.getenv("OPENROUTER_API_KEY");
    public static final String GEMINI_API_KEY     = System.getenv("GEMINI_API_KEY");
    public static final String NVIDIA_API_KEY     = System.getenv("NVIDIA_API_KEY");

    // ── Ollama local endpoint ────────────────────────────────────────────────
    public static final String OLLAMA_BASE_URL = System.getProperty("ollama.url", "http://localhost:11434");

    // ── Pipeline settings ────────────────────────────────────────────────────
    // When true, skip the LLM tailoring pipeline entirely and render resume.txt as-is.
    // Override at runtime with -Dignore.llms=true
    public static final boolean IGNORE_LLMS = Boolean.parseBoolean(
            System.getProperty("ignore.llms", "true")
    );
    public static final String JD_PATH         = "jd.txt";
    public static final String OUTPUT_BASE_DIR = System.getProperty("user.home") + "/clauderesume";
    public static final String RENDER_SCRIPT   = "render/render.js";
    public static final String STYLE           = "classic";
    public static final int    MAX_BULLETS     = 10;
    public static final int    MAX_LOOP_ITER   = 3;
    public static final int    SCORE_THRESHOLD = 6;
}
