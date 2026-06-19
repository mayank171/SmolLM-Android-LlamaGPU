package io.shubham0204.startwithsmollm.benchmark

/**
 * Benchmark dataset for the WIPO Annual Financial Report and Financial Statements 2020.
 * Each item contains:
 *   - query: the question to send to the RAG pipeline
 *   - expectedAnswer: human-readable reference answer (for the report)
 *   - keyPhrases: short phrases that MUST appear (case-insensitive) in a correct
 *     answer. We score by counting how many appear in the model's response.
 *
 * Scoring is intentionally lenient (key-phrase recall) since small on-device
 * models will paraphrase heavily. A response that mentions most key phrases is
 * considered "correct" for the purpose of comparing models against each other.
 */
object RagBenchmark {

    data class QA(
        val id: String,
        val query: String,
        val expectedAnswer: String,
        val keyPhrases: List<String>
    )

    val questions: List<QA> = listOf(
        QA(
            id = "q1_surplus_2020",
            query = "What was WIPO's surplus for the year 2020?",
            expectedAnswer = "Surplus of 135.9 million Swiss francs in 2020, compared to 97.7 million in 2019.",
            keyPhrases = listOf("135.9", "surplus", "Swiss francs")
        ),
        QA(
            id = "q2_revenue_expenses",
            query = "What were WIPO's total revenue and total expenses in 2020?",
            expectedAnswer = "Total revenue 468.3 million and total expenses 365.8 million Swiss francs.",
            keyPhrases = listOf("468.3", "365.8", "revenue", "expenses")
        ),
        QA(
            id = "q3_investment_gains",
            query = "How much did WIPO achieve in investment gains in 2020?",
            expectedAnswer = "Investment gains of 33.4 million Swiss francs in 2020 (vs 42.1 million in 2019).",
            keyPhrases = listOf("33.4", "investment", "gains")
        ),
        QA(
            id = "q4_pct_share",
            query = "What share of total revenue did PCT system fees represent in 2020?",
            expectedAnswer = "PCT system fees accounted for 76.6% of total revenue, the largest source.",
            keyPhrases = listOf("76.6", "PCT", "largest")
        ),
        QA(
            id = "q5_pct_revenue",
            query = "How much did PCT system fee revenue change in 2020 versus 2019?",
            expectedAnswer = "Rose 6.1% to 358.6 million Swiss francs (from 338.1 million in 2019).",
            keyPhrases = listOf("358.6", "6.1", "PCT")
        ),
        QA(
            id = "q6_pct_filings",
            query = "How many PCT applications were filed in 2020?",
            expectedAnswer = "About 275,900 PCT applications, a record, up 4.0% from 265,381 in 2019.",
            keyPhrases = listOf("275,900", "4.0", "record")
        ),
        QA(
            id = "q7_madrid",
            query = "How did Madrid system revenue change in 2020?",
            expectedAnswer = "Fell 0.8% to 76.2 million Swiss francs; international trademark applications " +
                "totaled around 63,800, the first decline since 2008-2009.",
            keyPhrases = listOf("76.2", "0.8", "Madrid")
        ),
        QA(
            id = "q8_hague",
            query = "What happened to Hague system revenue in 2020?",
            expectedAnswer = "Hague revenue increased 26.4% to 6.7 million Swiss francs.",
            keyPhrases = listOf("6.7", "26.4", "Hague")
        ),
        QA(
            id = "q9_covid_spend",
            query = "How much did WIPO spend directly on COVID-19 related items in 2020?",
            expectedAnswer = "Approximately 3.6 million Swiss francs, mainly IT equipment and services " +
                "for remote and hybrid working.",
            keyPhrases = listOf("3.6", "COVID", "IT")
        ),
        QA(
            id = "q10_missions",
            query = "How did the cost of missions for staff and consultants change in 2020?",
            expectedAnswer = "Fell from 5.7 million Swiss francs in 2019 to 0.5 million in 2020 due to travel bans.",
            keyPhrases = listOf("5.7", "0.5", "missions")
        ),
        QA(
            id = "q11_personnel",
            query = "What was WIPO's largest expense category in 2020 and how big was it?",
            expectedAnswer = "Personnel expenditure of 233.7 million Swiss francs, 63.9% of total expenses.",
            keyPhrases = listOf("233.7", "personnel", "63.9")
        ),
        QA(
            id = "q12_travel_training",
            query = "By how much did travel, training and grants expenses fall in 2020?",
            expectedAnswer = "Fell 89.7%, from 17.5 million Swiss francs in 2019 to 1.8 million in 2020.",
            keyPhrases = listOf("89.7", "17.5", "1.8")
        ),
        QA(
            id = "q13_balance_sheet",
            query = "What were WIPO's net assets, total assets and total liabilities at the end of 2020?",
            expectedAnswer = "Net assets 387.1M; total assets 1,390.9M; total liabilities 1,003.8M Swiss francs.",
            keyPhrases = listOf("387.1", "1,390.9", "1,003.8")
        ),
        QA(
            id = "q14_cash",
            query = "What was WIPO's combined cash and investment balance at year-end 2020?",
            expectedAnswer = "932.0 million Swiss francs, 177.9 million higher than 754.1 million in 2019; " +
                "equal to 67.0% of total assets.",
            keyPhrases = listOf("932.0", "754.1", "67.0")
        ),
        QA(
            id = "q15_ashi",
            query = "What was the ASHI liability and what share of employee benefits did it represent?",
            expectedAnswer = "452.8 million Swiss francs, 91.4% of total employee benefit liabilities of 495.3M.",
            keyPhrases = listOf("452.8", "91.4", "ASHI")
        ),
        QA(
            id = "q16_discount_rate",
            query = "How did the discount rate used for the ASHI liability change in 2020?",
            expectedAnswer = "Lowered from 0.50% to 0.30%, contributing to the increase in the ASHI liability.",
            keyPhrases = listOf("0.50", "0.30", "discount")
        ),
        QA(
            id = "q17_voluntary",
            query = "How much did voluntary contribution revenue change in 2020 and why?",
            expectedAnswer = "Fell 46.8% to 5.8 million Swiss francs because, under Special Accounts, " +
                "revenue is recognised as expense is incurred and pandemic delays lowered expenditure.",
            keyPhrases = listOf("5.8", "46.8", "voluntary")
        ),
        QA(
            id = "q18_dg",
            query = "Who became WIPO's Director General in 2020 and when did the term start?",
            expectedAnswer = "Daren Tang, appointed May 8, 2020, began a six-year term on October 1, 2020, " +
                "succeeding Francis Gurry.",
            keyPhrases = listOf("Daren Tang", "October 1", "six-year")
        ),
        QA(
            id = "q19_member_states",
            query = "How many Member States does WIPO have and what is its role?",
            expectedAnswer = "WIPO is a specialized agency of the United Nations with 193 Member States; " +
                "global forum for intellectual property services, policy, information and cooperation.",
            keyPhrases = listOf("193", "Member States", "United Nations")
        ),
        QA(
            id = "q20_productivity",
            query = "What productivity level did WIPO's PCT, Madrid and Hague systems maintain by December 2020?",
            expectedAnswer = "Productivity indicators for the PCT, Madrid and Hague systems were all at 98% " +
                "or above by December 2020.",
            keyPhrases = listOf("98", "PCT", "Madrid", "Hague")
        )
    )

    /**
     * Lenient synonym map: a key phrase is considered "matched" if the response
     * contains the key phrase OR any of its accepted variants. This avoids
     * penalising paraphrased answers from small on-device LLMs.
     */
    private val synonyms: Map<String, List<String>> = mapOf(
        "CUDA graph" to listOf("cuda graph", "cuda-graph", "cudagraph", "graph capture", "replayable graph"),
        "CUDA" to listOf("cuda", "torch.compile", "compiled mode"),
        "dispatch" to listOf("dispatch", "kernel launch", "per-kernel", "python overhead"),
        "overhead" to listOf("overhead", "overhead-bound", "dispatch cost"),
        "decode" to listOf("decode", "decoding", "forward pass", "token generation"),
        "bandwidth" to listOf("bandwidth", "memory bandwidth", "tb/s", "gb/s"),
        "8 GB" to listOf("8 gb", "8gb", "8 gigabyte"),
        "scheduler" to listOf("scheduler", "serving scheduler", "vllm scheduler"),
        "wash" to listOf("wash", "no improvement", "1.5%", "marginal"),
        "max-seqs" to listOf("max-seqs", "max_seqs", "max sequences", "32"),
        "back-pressure" to listOf("back-pressure", "backpressure", "back pressure", "admission queue"),
        "saturation" to listOf("saturation", "saturated", "past the knee", "knee"),
        "prefill" to listOf("prefill", "pre-fill", "chunked prefill"),
        "compiled" to listOf("compiled", "compilation", "torch.compile", "compiled mode"),
        "validated" to listOf("validated", "verified", "confirmed", "7-for-7", "all seven"),
        "seven" to listOf("seven", "7", "7-for-7"),
        "one variable" to listOf("one variable", "single variable", "one knob"),
        "baseline" to listOf("baseline", "control", "measured control"),
        "bottleneck" to listOf("bottleneck", "binding constraint", "limiting factor"),
        "multimodal" to listOf("multimodal", "multi-modal", "image", "vision"),
        "throughput" to listOf("throughput", "tokens/s", "tok/s"),
        "ITL" to listOf("itl", "inter-token latency", "latency"),
        "TTFT" to listOf("ttft", "time to first token"),
        "GPU" to listOf("gpu", "gpus", "h100", "h200"),
        "Arena" to listOf("arena", "leaderboard", "infertutor"),
        "vLLM" to listOf("vllm", "v-llm")
    )
    
    private fun phraseMatches(response: String, phrase: String): Boolean {
        val phraseL = phrase.lowercase()
        if (response.contains(phraseL)) return true
        val variants = synonyms[phrase] ?: return false
        return variants.any { response.contains(it.lowercase()) }
    }
    
    /**
     * Score a model response against an expected QA.
     * Returns: fraction of key phrases that appear in [response] (case-insensitive,
     * with synonym matching), in [0.0, 1.0].
     */
    fun scoreResponse(response: String, qa: QA): ScoreResult {
        val lower = response.lowercase()
        val hits = qa.keyPhrases.filter { phraseMatches(lower, it) }
        val missed = qa.keyPhrases - hits.toSet()
        val frac = if (qa.keyPhrases.isEmpty()) 1.0f else hits.size.toFloat() / qa.keyPhrases.size
        return ScoreResult(
            score = frac,
            matchedPhrases = hits,
            missedPhrases = missed
        )
    }

    data class ScoreResult(
        val score: Float,                       // 0.0 .. 1.0
        val matchedPhrases: List<String>,
        val missedPhrases: List<String>
    )
}
