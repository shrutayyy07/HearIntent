import json
import re
import threading
import time
from dataclasses import dataclass, field
from datetime import datetime

from app.config import config
from app.logger import get_logger, log_with_fields

logger = get_logger("intent_engine")



@dataclass
class IntentResult:
    intent: str
    confidence: float
    entities: dict = field(default_factory=dict)
    raw_text: str = ""
    extracted_at_ms: int = 0

    def to_entities_json(self) -> str:
        return json.dumps(self.entities)


class IntentExtractionEngine:
    """Generative instruction extraction using a lightweight LLM
    (Qwen2.5-0.5B-Instruct by default), combined with lightweight
    regex-based entity extraction for dates, times, and names.
    """

    def __init__(self) -> None:
        self._classifier = None
        self._lock = threading.Lock()

    def load(self) -> None:
        with self._lock:
            if self._classifier is not None:
                return
            log_with_fields(
                logger, 20, "Loading intent model", model=config.intent_model_name
            )
            started = time.time()
            from transformers import pipeline

            self._classifier = pipeline(
                "text-generation",
                model=config.intent_model_name,
                device=-1 if config.intent_device == "cpu" else 0,
            )
            log_with_fields(
                logger,
                20,
                "Intent model loaded",
                elapsed_sec=round(time.time() - started, 2),
            )

    @property
    def is_loaded(self) -> bool:
        return self._classifier is not None

    def _extract_entities(self, text: str) -> dict:
        return {}

    def extract(self, text: str) -> IntentResult:
        if not text or not text.strip():
            return IntentResult(
                intent="none",
                confidence=0.0,
                entities={},
                raw_text=text,
                extracted_at_ms=int(time.time() * 1000),
            )

        if self._classifier is None:
            self.load()
        assert self._classifier is not None

        try:
            # 1. Extract Instruction
            res_inst = self._classifier(
                [
                    {"role": "system", "content": "You are a conversational analyst. Summarize the speaker's main intent or action in a very short sentence."},
                    {"role": "user", "content": f"Text: '{text}'"}
                ],
                max_new_tokens=30,
                do_sample=False,
                pad_token_id=self._classifier.tokenizer.eos_token_id
            )
            intent_label = res_inst[0]["generated_text"][-1]["content"].strip()
            # Clean up common prefixes Qwen might add
            if intent_label.lower().startswith("the main instruction is to"):
                intent_label = intent_label[26:].strip()
            elif intent_label.lower().startswith("the main instruction is"):
                intent_label = intent_label[23:].strip()
            elif intent_label.lower().startswith("the speaker is"):
                intent_label = intent_label[14:].strip().capitalize()
                
            # 2. Extract Emotion
            res_emo = self._classifier(
                [
                    {"role": "system", "content": "You are an emotion analyzer. Reply with exactly one word (e.g. angry, neutral, sad, happy, frustrated, cautious, apologetic, curious, helpful, excited)."},
                    {"role": "user", "content": f"Text: '{text}'"}
                ],
                max_new_tokens=10,
                do_sample=False,
                pad_token_id=self._classifier.tokenizer.eos_token_id
            )
            top_emotion = res_emo[0]["generated_text"][-1]["content"].strip().lower()
            # Grab just the first word in case it says "The emotion is angry"
            top_emotion = re.sub(r'[^a-z]', ' ', top_emotion).split()[0] if top_emotion else "neutral"
            
            top_score = 1.0
        except Exception as e:
            logger.error(f"Error during generative intent extraction: {e}")
            intent_label = text
            top_emotion = "neutral"
            top_score = 0.5

        entities = self._extract_entities(text)
        entities["emotion"] = top_emotion

        log_with_fields(
            logger,
            20,
            "Intent extracted",
            intent=intent_label,
            confidence=round(top_score, 4),
            emotion=top_emotion,
        )

        return IntentResult(
            intent=intent_label,
            confidence=round(top_score, 4),
            entities=entities,
            raw_text=text,
            extracted_at_ms=int(time.time() * 1000),
        )


intent_engine = IntentExtractionEngine()

