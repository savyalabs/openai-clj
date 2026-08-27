# Examples cookbook

These plain `.clj` namespaces use the public APIs in this checkout and make
live requests only when their `run-*` function is evaluated. They contain no
credentials. Install the project dependencies first, then set the required
environment variable in your shell:

```sh
export OPENAI_API_KEY=your-key
```

Run a workflow from the repository root with:

```sh
clojure -Sdeps '{:paths ["src" "resources" "."]}' -M -e "(require 'examples.realtime) (examples.realtime/run-session)"
clojure -Sdeps '{:paths ["src" "resources" "."]}' -M -e "(require 'examples.tool-calling) (println (:text (examples.tool-calling/run-tool-loop)))"
clojure -Sdeps '{:paths ["src" "resources" "."]}' -M -e "(require 'examples.batch) (println (examples.batch/run-batch))"
```

For a REPL, start it from the repository root with
`clojure -Sdeps '{:paths ["src" "resources" "."]}' -M`. Then evaluate the
`(comment ...)` block at the bottom of the relevant file, or require it and call
its `run-*` function. The
Realtime workflow accepts `OPENAI_REALTIME_CLIENT_SECRET` in place of the API
key and supports `OPENAI_REALTIME_MODEL`, `OPENAI_REALTIME_INSTRUCTIONS`, and
`OPENAI_REALTIME_PROMPT`. The tool and batch examples accept the environment
variables documented in their namespace docstrings.

The batch workflow uploads a JSONL file in memory, submits a `/v1/responses`
batch, polls with exponential backoff, and downloads the output file.
