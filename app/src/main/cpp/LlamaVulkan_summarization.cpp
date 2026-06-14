// Background summarization implementation for LlamaVulkan
// This will be added to LlamaVulkan.cpp

std::string LlamaVulkan::summarizeMessages(int startIdx, int count) {
    if (startIdx < 0 || count <= 0 || startIdx + count > _messages.size()) {
        LOGe("Invalid summarization range: startIdx=%d, count=%d, total=%zu", 
             startIdx, count, _messages.size());
        return "";
    }
    
    LOGi("=== SUMMARIZING MESSAGES: startIdx=%d, count=%d ===", startIdx, count);
    
    // Build summarization prompt
    std::string conversationText = "";
    for (int i = startIdx; i < startIdx + count; i++) {
        const auto& msg = _messages[i];
        conversationText += std::string(msg.role) + ": " + std::string(msg.content) + "\n\n";
    }
    
    // Create summarization prompt
    std::string summaryPrompt = 
        "You are a conversation summarizer. Create a concise summary of the following conversation, "
        "preserving all important facts, context, and key points.\n\n"
        "Conversation:\n" + conversationText + "\n"
        "Provide a summary in 2-3 sentences that captures the essence of this discussion:\n";
    
    LOGi("Summarization prompt length: %zu characters", summaryPrompt.length());
    
    // Tokenize the prompt
    std::vector<llama_token> promptTokens = common_tokenize(
        llama_model_get_vocab(_model), 
        summaryPrompt, 
        true,  // add_special
        true   // parse_special
    );
    
    LOGi("Prompt tokens: %zu", promptTokens.size());
    
    // Create batch for summarization
    llama_batch* summaryBatch = new llama_batch();
    summaryBatch->token = promptTokens.data();
    summaryBatch->n_tokens = promptTokens.size();
    summaryBatch->pos = nullptr;
    summaryBatch->n_seq_id = nullptr;
    summaryBatch->seq_id = nullptr;
    summaryBatch->logits = nullptr;
    
    // Process the prompt
    if (llama_decode(_ctx, *summaryBatch) < 0) {
        LOGe("Failed to decode summarization prompt");
        delete summaryBatch;
        return "";
    }
    
    // Generate summary (max 150 tokens)
    std::string summary = "";
    int maxSummaryTokens = 150;
    llama_token currentToken;
    
    for (int i = 0; i < maxSummaryTokens; i++) {
        currentToken = llama_sampler_sample(_sampler, _ctx, -1);
        
        // Check for end of generation
        if (llama_vocab_is_eog(llama_model_get_vocab(_model), currentToken)) {
            break;
        }
        
        // Convert token to text
        std::string piece = common_token_to_piece(_ctx, currentToken, true);
        summary += piece;
        
        // Prepare for next token
        summaryBatch->token = &currentToken;
        summaryBatch->n_tokens = 1;
        
        if (llama_decode(_ctx, *summaryBatch) < 0) {
            LOGe("Failed to decode during summary generation");
            break;
        }
    }
    
    delete summaryBatch;
    
    // Trim whitespace
    summary.erase(0, summary.find_first_not_of(" \n\r\t"));
    summary.erase(summary.find_last_not_of(" \n\r\t") + 1);
    
    LOGi("Generated summary (%zu chars): %s", summary.length(), summary.c_str());
    
    return summary;
}

void LlamaVulkan::rebuildCacheWithSummary(const char* summary, int keepRecentN) {
    LOGi("=== REBUILDING CACHE WITH SUMMARY ===");
    LOGi("Summary: %s", summary);
    LOGi("Keeping recent %d messages", keepRecentN);
    
    if (keepRecentN < 0 || keepRecentN > _messages.size()) {
        LOGe("Invalid keepRecentN: %d (total messages: %zu)", keepRecentN, _messages.size());
        return;
    }
    
    // Clear KV cache
    llama_memory_t mem = llama_get_memory(_ctx);
    if (mem) {
        llama_memory_clear(mem, true);
        LOGi("KV cache cleared");
    }
    
    // Build new message list: [System + Summary + Recent messages]
    std::vector<llama_chat_message> newMessages;
    
    // Keep system message if it exists
    if (!_messages.empty() && strcmp(_messages[0].role, "system") == 0) {
        newMessages.push_back(_messages[0]);
    }
    
    // Add summary as a system message
    llama_chat_message summaryMsg;
    summaryMsg.role = strdup("system");
    summaryMsg.content = strdup((std::string("Earlier conversation summary: ") + summary).c_str());
    newMessages.push_back(summaryMsg);
    
    // Add recent messages
    int startIdx = _messages.size() - keepRecentN;
    for (int i = startIdx; i < _messages.size(); i++) {
        newMessages.push_back(_messages[i]);
    }
    
    LOGi("New message count: %zu (was %zu)", newMessages.size(), _messages.size());
    
    // Update internal message list
    _messages = newMessages;
    
    // Rebuild prompt with chat template
    std::vector<common_chat_msg> messages;
    for (const llama_chat_message& message : _messages) {
        common_chat_msg msg;
        msg.role = message.role;
        msg.content = message.content;
        messages.push_back(msg);
    }
    
    common_chat_templates_inputs inputs;
    inputs.use_jinja = true;
    inputs.messages = messages;
    auto templates = common_chat_templates_init(_model, _chatTemplate);
    std::string fullPrompt = common_chat_templates_apply(templates.get(), inputs).prompt;
    
    // Tokenize full prompt
    _cachedTokens = common_tokenize(llama_model_get_vocab(_model), fullPrompt, true, true);
    
    LOGi("Rebuilding KV cache with %zu tokens", _cachedTokens.size());
    
    // Process all tokens to rebuild KV cache
    llama_batch* rebuildBatch = new llama_batch();
    rebuildBatch->token = _cachedTokens.data();
    rebuildBatch->n_tokens = _cachedTokens.size();
    rebuildBatch->pos = nullptr;
    rebuildBatch->n_seq_id = nullptr;
    rebuildBatch->seq_id = nullptr;
    rebuildBatch->logits = nullptr;
    
    if (llama_decode(_ctx, *rebuildBatch) < 0) {
        LOGe("Failed to rebuild KV cache");
        delete rebuildBatch;
        return;
    }
    
    delete rebuildBatch;
    
    // Update context usage
    _nCtxUsed = _cachedTokens.size();
    
    LOGi("KV cache rebuilt successfully: %d tokens", _nCtxUsed);
    LOGi("Context usage: %d%%", (_nCtxUsed * 100) / llama_n_ctx(_ctx));
}
