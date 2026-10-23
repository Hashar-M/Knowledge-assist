# Glossary

| Term | Plain-language meaning |
|---|---|
| **LLM** | A model trained on lots of text to predict the next text. Chat assistants are built on it. |
| **Training / inference** | Training builds the model (done by the provider, once). Inference is each API call. Knowledge is frozen at inference. |
| **Knowledge cutoff** | The date after which the model's training data stops. |
| **Token** | The unit of text the model reads and writes (about 4 English characters). Cost, speed and limits are measured in tokens. |
| **Prompt** | Everything sent to the model in one call: system message, user message, history, pasted context. |
| **System / user message** | System = standing rules. User = the question. |
| **Context window** | Max tokens (prompt + answer) the model can handle in one call. |
| **Hallucination** | A fluent, confident answer that is wrong or invented. |
| **Grounding** | Forcing answers to come from supplied evidence instead of the model's memory. |
| **Fine-tuning** | Further training a model on your data. Good for style and behaviour, poor for storing changing facts. |
| **RAG** | Retrieval-Augmented Generation: retrieve relevant text, add it to the prompt, then generate. |
| **Retrieval** | Finding the most relevant pieces of your documents for a question. |
| **Chunk** | A small piece of a document (a few paragraphs) that is indexed and retrieved as a unit. (Chapter 7) |
| **Embedding** | A list of numbers representing the meaning of a text, so similar meanings are close together. (Chapter 2) |
| **Vector search / kNN** | Finding the stored embeddings closest to the question's embedding. (Chapters 2-3) |
| **BM25** | Classic keyword relevance scoring (what Elasticsearch has always done). (Chapter 11) |
| **Citation** | A pointer from an answer back to the source chunk it used. (Chapter 13) |
| **ChatModel / ChatClient** | Spring AI's low-level and fluent APIs for calling a chat model. |
| **Advisor** | A Spring AI interceptor around a model call (adds context, memory, logging). (Chapter 9) |
| **Prompt injection** | Malicious text in a document or question that tries to override your instructions. (Chapter 23) |
| **Golden set** | A fixed list of questions with known correct sources, used to measure quality. (Chapter 10) |
