# Images and Audio

`openai.images` wraps image generation, edits, variations, and stream events.
Binary inputs accept paths, byte arrays, or input streams.

`openai.audio` wraps speech creation, transcription, and translation.

```clojure
(images/generate client {:model "gpt-image-1" :prompt "A map"})
(images/generate client {:model "gpt-image-2.6" :prompt "An animated map"
                         :complexity :medium :frame-count 8 :frame-duration-ms 100
                         :svg {:model "gpt-image-2.6"
                               :reasoning {:effort :low}}})
(audio/create-transcription client {:file "speech.wav" :model "whisper-1"})
```
