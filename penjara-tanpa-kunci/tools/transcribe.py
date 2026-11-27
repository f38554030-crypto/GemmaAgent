import sys, json, time, wave
import numpy as np
from faster_whisper import WhisperModel

wav, out, model_name = sys.argv[1], sys.argv[2], (sys.argv[3] if len(sys.argv) > 3 else "large-v3")
t0 = time.time()
model = WhisperModel(model_name, device="cpu", compute_type="int8", cpu_threads=4)
w = wave.open(wav); assert w.getframerate()==16000 and w.getnchannels()==1
audio = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32)/32768.0
segments, info = model.transcribe(
    audio, language="id", word_timestamps=True, vad_filter=False, beam_size=5,
    condition_on_previous_text=False,
)
res = []
for s in segments:
    res.append({
        "start": round(s.start, 2), "end": round(s.end, 2), "text": s.text.strip(),
        "words": [{"w": w.word.strip(), "s": round(w.start, 2), "e": round(w.end, 2), "p": round(w.probability, 2)} for w in s.words],
    })
    print(f"[{s.start:7.2f} -> {s.end:7.2f}] {s.text.strip()}", flush=True)
json.dump({"model": model_name, "lang_prob": info.language_probability, "segments": res}, open(out, "w"), ensure_ascii=False, indent=1)
print(f"done in {time.time()-t0:.0f}s", flush=True)
