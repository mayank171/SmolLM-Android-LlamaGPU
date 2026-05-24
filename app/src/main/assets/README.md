# RAG Assets

This folder should contain the following files for neural embeddings:

## Required Files

### 1. `all-MiniLM-L6-v2.onnx` (~23 MB)
The ONNX model for generating text embeddings.

**Download:**
```bash
# Option 1: From Hugging Face
wget https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx -O all-MiniLM-L6-v2.onnx

# Option 2: Using Python
pip install optimum[onnxruntime]
optimum-cli export onnx --model sentence-transformers/all-MiniLM-L6-v2 ./minilm
mv ./minilm/model.onnx all-MiniLM-L6-v2.onnx
```

### 2. `vocab.txt` (~226 KB)
The WordPiece vocabulary file for tokenization.

**Download:**
```bash
wget https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/vocab.txt
```

## Without These Files

If these files are not present, the app will fall back to TF-IDF based embeddings, which:
- Work without any external files
- Are faster but less accurate
- Use keyword matching instead of semantic understanding

## File Sizes

| File | Size |
|------|------|
| all-MiniLM-L6-v2.onnx | ~23 MB |
| vocab.txt | ~226 KB |
| **Total** | ~23.2 MB |

## Model Info

- **Model:** all-MiniLM-L6-v2
- **Embedding Dimension:** 384
- **Max Sequence Length:** 128 tokens
- **License:** Apache 2.0
